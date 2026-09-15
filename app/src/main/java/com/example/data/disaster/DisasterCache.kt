package com.example.data.disaster

import com.example.data.routing.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Freshness/expiry rules for on-device disaster-event caching (pure logic).
 *
 * - LIVE: fetched within the fresh TTL, displayed as live data.
 * - RECENT: fetch a few hours old, still displayed (clearly labeled with age).
 * - CACHED: older fetch, still usable offline (labeled "Cached").
 * - Stale beyond MAX_AGE: dropped — old hazard data misleads in emergencies.
 */
object DisasterCachePolicy {
  const val FRESH_TTL_MILLIS: Long = 15L * 60L * 1000L
  const val RECENT_TTL_MILLIS: Long = 6L * 60L * 60L * 1000L
  const val MAX_AGE_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L

  fun isFresh(fetchedAtMillis: Long, nowMillis: Long): Boolean =
    fetchedAtMillis > 0L && nowMillis - fetchedAtMillis in 0 until FRESH_TTL_MILLIS

  fun isRecent(fetchedAtMillis: Long, nowMillis: Long): Boolean =
    fetchedAtMillis > 0L && nowMillis - fetchedAtMillis < RECENT_TTL_MILLIS

  fun isUsable(fetchedAtMillis: Long, nowMillis: Long): Boolean =
    fetchedAtMillis > 0L && nowMillis - fetchedAtMillis <= MAX_AGE_MILLIS

  /** Freshness label for a single provider shard. */
  fun label(fetchedAtMillis: Long, nowMillis: Long): String = when {
    fetchedAtMillis <= 0L -> "Unavailable"
    isFresh(fetchedAtMillis, nowMillis) -> "Live"
    isRecent(fetchedAtMillis, nowMillis) -> "Recent"
    isUsable(fetchedAtMillis, nowMillis) -> "Cached"
    else -> "Expired"
  }
}

/** One cached provider shard (events + fetch time + provider status). */
data class CachedProviderFeed(
  val events: List<DisasterEvent>,
  val fetchedAtMillis: Long,
  val statusMessage: String?
)

/** On-device disaster cache boundary (in-memory in tests, files in production). */
interface DisasterCache {
  fun read(source: DisasterSource): CachedProviderFeed?
  fun write(source: DisasterSource, feed: CachedProviderFeed)
  fun clear()
}

/** In-memory implementation (unit tests, default ViewModel construction). */
class MemoryDisasterCache : DisasterCache {
  private val map = mutableMapOf<DisasterSource, CachedProviderFeed>()
  override fun read(source: DisasterSource) = map[source]
  override fun write(source: DisasterSource, feed: CachedProviderFeed) {
    map[source] = feed
  }
  override fun clear() = map.clear()
}

/**
 * File-backed cache (one JSON shard per provider) in the app's private
 * storage; corrupt shards are dropped instead of crashing. Follows the
 * production-proven NewsFileCache convention.
 */
class DisasterFileCache(cacheDir: File) : DisasterCache {

  private val dir: File = cacheDir.apply { mkdirs() }

  override fun read(source: DisasterSource): CachedProviderFeed? {
    val file = shardFile(source)
    if (!file.isFile) return null
    return try {
      val root = JSONObject(file.readText())
      val arr = root.optJSONArray("events") ?: JSONArray()
      val events = mutableListOf<DisasterEvent>()
      for (i in 0 until arr.length()) {
        arr.optJSONObject(i)?.let { obj -> deserializeEvent(obj)?.let(events::add) }
      }
      CachedProviderFeed(
        events = events,
        fetchedAtMillis = root.optLong("fetchedAtMillis", 0L),
        statusMessage = root.optString("statusMessage").takeIf { it.isNotBlank() }
      )
    } catch (e: Exception) {
      file.delete()
      null
    }
  }

  override fun write(source: DisasterSource, feed: CachedProviderFeed) {
    try {
      val arr = JSONArray()
      feed.events.forEach { arr.put(serializeEvent(it)) }
      shardFile(source).writeText(
        JSONObject()
          .put("fetchedAtMillis", feed.fetchedAtMillis)
          .put("statusMessage", feed.statusMessage ?: "")
          .put("events", arr)
          .toString()
      )
    } catch (e: Exception) {
      // Non-fatal: live rendering still works without cache persistence.
    }
  }

  override fun clear() {
    DisasterSource.values().forEach { shardFile(it).delete() }
  }

  private fun shardFile(source: DisasterSource): File = File(dir, "disaster_${source.name}.json")
}

// ============================================================================
// (De)serialization — the cache stays provider-agnostic via the normalized
// model so a later backend can reuse exactly this wire format.
// ============================================================================

internal fun serializeEvent(event: DisasterEvent): JSONObject = JSONObject()
  .put("id", event.id)
  .put("source", event.source.name)
  .put("sourceEventId", event.sourceEventId)
  .put("type", event.disasterType.name)
  .put("title", event.title)
  .put("description", event.description)
  .put("lat", event.latitude ?: Double.NaN)
  .put("lon", event.longitude ?: Double.NaN)
  .put("severity", event.severity.name)
  .put("confidence", event.confidence.name)
  .put("confidenceNote", event.confidenceNote ?: "")
  .put("observedAt", event.observedAtMillis)
  .put("updatedAt", event.updatedAtMillis)
  .put("expiresAt", event.expiresAtMillis ?: 0L)
  .put("status", event.status.name)
  .put("origin", event.origin.name)
  .put("affectedArea", event.affectedAreaLabel ?: "")
  .put("url", event.url ?: "")
  .put("geometryType", event.geometry.type.name)
  .put("polygon", JSONArray().apply {
    (event.geometry as? EventGeometry.Polygon)?.ring?.forEach { p ->
      put(JSONObject().put("lat", p.lat).put("lon", p.lon))
    }
  })

internal fun deserializeEvent(obj: JSONObject): DisasterEvent? {
  val source = runCatching { DisasterSource.valueOf(obj.optString("source")) }.getOrNull()
    ?: return null
  val type = runCatching { DisasterType.valueOf(obj.optString("type")) }.getOrNull() ?: return null
  val severity = runCatching {
    com.example.data.model.HazardSeverity.valueOf(obj.optString("severity"))
  }.getOrNull() ?: return null
  val sourceEventId = obj.optString("sourceEventId")
  if (sourceEventId.isBlank()) return null

  val geometry = when (obj.optString("geometryType")) {
    GeometryType.POLYGON.name -> {
      val ring = mutableListOf<GeoPoint>()
      val arr = obj.optJSONArray("polygon")
      if (arr != null) {
        for (i in 0 until arr.length()) {
          val p = arr.optJSONObject(i) ?: continue
          val lat = p.optDouble("lat", Double.NaN)
          val lon = p.optDouble("lon", Double.NaN)
          if (!lat.isNaN() && !lon.isNaN()) ring += GeoPoint(lat, lon)
        }
      }
      if (ring.size >= 3) EventGeometry.Polygon(ring) else EventGeometry.Point(
        IndiaGeo.CENTER_LAT, IndiaGeo.CENTER_LON
      )
    }
    else -> {
      val lat = obj.optDouble("lat", Double.NaN)
      val lon = obj.optDouble("lon", Double.NaN)
      if (lat.isNaN() || lon.isNaN()) {
        EventGeometry.Point(IndiaGeo.CENTER_LAT, IndiaGeo.CENTER_LON)
      } else {
        EventGeometry.Point(lat, lon)
      }
    }
  }

  return DisasterEvent(
    id = obj.optString("id").ifBlank { "cached-${source.name}-$sourceEventId" },
    source = source,
    sourceEventId = sourceEventId,
    disasterType = type,
    title = obj.optString("title").ifBlank { "Disaster event" },
    description = obj.optString("description"),
    geometry = geometry,
    latitude = (geometry as? EventGeometry.Point)?.lat,
    longitude = (geometry as? EventGeometry.Point)?.lon,
    severity = severity,
    confidence = runCatching { EventConfidence.valueOf(obj.optString("confidence")) }
      .getOrDefault(EventConfidence.NOT_PROVIDED),
    confidenceNote = obj.optString("confidenceNote").takeIf { it.isNotBlank() },
    observedAtMillis = obj.optLong("observedAt", 0L),
    updatedAtMillis = obj.optLong("updatedAt", 0L),
    expiresAtMillis = obj.optLong("expiresAt", 0L).takeIf { it > 0 },
    status = runCatching { EventStatus.valueOf(obj.optString("status")) }
      .getOrDefault(EventStatus.ACTIVE),
    origin = runCatching { EventOrigin.valueOf(obj.optString("origin")) }
      .getOrDefault(EventOrigin.OBSERVED),
    affectedAreaLabel = obj.optString("affectedArea").takeIf { it.isNotBlank() },
    url = obj.optString("url").takeIf { it.isNotBlank() },
    details = deserializeDetails(obj.optJSONObject("details"))
  )
}

