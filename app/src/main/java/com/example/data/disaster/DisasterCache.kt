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
    fetchedAtMillis > 0L && nowMillis - fetchedAtMillis in 0 until RECENT_TTL_MILLIS

  fun isUsable(fetchedAtMillis: Long, nowMillis: Long): Boolean =
    fetchedAtMillis > 0L && nowMillis - fetchedAtMillis in 0..MAX_AGE_MILLIS

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
      val target = shardFile(source)
      // Atomic write: a process kill mid-write must not leave a truncated
      // shard that would blank the whole cache on next read.
      val tmp = File(dir, "${target.name}.tmp")
      tmp.writeText(
        JSONObject()
          .put("fetchedAtMillis", feed.fetchedAtMillis)
          .put("statusMessage", feed.statusMessage ?: "")
          .put("events", arr)
          .toString()
      )
if (!tmp.renameTo(target)) {
        target.writeText(tmp.readText())
        tmp.delete()
      }
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
  // JSON has no NaN: absent coordinates are stored as JSON null (never a
  // fabricated number). Polygon/line events legitimately have no point fix.
  .put("lat", event.latitude?.takeIf { !it.isNaN() } ?: JSONObject.NULL)
  .put("lon", event.longitude?.takeIf { !it.isNaN() } ?: JSONObject.NULL)
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
  .put("polygon", serializePointList((event.geometry as? EventGeometry.Polygon)?.ring))
  .put("points", serializePointList((event.geometry as? EventGeometry.MultiPoint)?.points))
  .put("line", serializePointList((event.geometry as? EventGeometry.Line)?.points))
  .put("rasterLayerId", (event.geometry as? EventGeometry.RasterLayer)?.layerId ?: "")
  .put("rasterLayerTitle", (event.geometry as? EventGeometry.RasterLayer)?.title ?: "")
  // Unlocated records round-trip as their own geometry kind with the provider's
  // area text; they are never rewritten as a coordinate.
  .put("unlocatedArea", (event.geometry as? EventGeometry.Unlocated)?.areaLabel ?: "")
  .put("details", serializeDetails(event.details))

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
      val ring = deserializePointList(obj.optJSONArray("polygon"))
      if (ring.size >= 3) EventGeometry.Polygon(ring) else degradedGeometry(obj)
    }
    GeometryType.MULTIPOINT.name -> {
      val points = deserializePointList(obj.optJSONArray("points"))
      if (points.isNotEmpty()) EventGeometry.MultiPoint(points) else degradedGeometry(obj)
    }
    GeometryType.LINE.name -> {
      val points = deserializePointList(obj.optJSONArray("line"))
      if (points.size >= 2) EventGeometry.Line(points) else degradedGeometry(obj)
    }
    GeometryType.RASTER_LAYER.name -> {
      val layerId = obj.optString("rasterLayerId")
      if (layerId.isNotBlank()) {
        EventGeometry.RasterLayer(layerId, obj.optString("rasterLayerTitle").ifBlank { layerId })
      } else {
        degradedGeometry(obj)
      }
    }
    GeometryType.UNLOCATED.name -> EventGeometry.Unlocated(
      obj.optString("unlocatedArea").takeIf { it.isNotBlank() }
    )
    else -> degradedGeometry(obj)
  } ?: return null // unlocatable shard — dropped, never pinned to a fake spot

  return DisasterEvent(
    id = obj.optString("id").ifBlank { "cached-${source.name}-$sourceEventId" },
    source = source,
    sourceEventId = sourceEventId,
    disasterType = type,
    title = obj.optString("title").ifBlank { "Disaster event" },
    description = obj.optString("description"),
    geometry = geometry,
    latitude = (geometry as? EventGeometry.Point)?.lat,
    longitude = (geometry as? EventGeometry.Point)?.lon, // stays null when UNLOCATED
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
    details = legacyDetails(obj, type)
  )
}

// ============================================================================
// Geometry + EventDetails round-trip helpers.
//
// Honest-corruption policy: a shard that cannot fully reconstruct a complex
// geometry degrades to the stored point coordinates when available — never to
// invented coordinates. When no usable location was persisted, the event is
// DROPPED (null) instead of being pinned to the India centre, which would
// fabricate a hazard over central India.
// ============================================================================

private fun serializePointList(points: List<GeoPoint>?): JSONArray = JSONArray().apply {
  points?.forEach { p -> put(JSONObject().put("lat", p.lat).put("lon", p.lon)) }
}

private fun deserializePointList(arr: JSONArray?): List<GeoPoint> {
  if (arr == null) return emptyList()
  val out = mutableListOf<GeoPoint>()
  for (i in 0 until arr.length()) {
    val p = arr.optJSONObject(i) ?: continue
    val lat = p.optDouble("lat", Double.NaN)
    val lon = p.optDouble("lon", Double.NaN)
    if (!lat.isNaN() && !lon.isNaN()) out += GeoPoint(lat, lon)
  }
  return out
}

/** Resolves the stored point fields for a degraded geometry fallback. */
private fun storedPoint(obj: JSONObject): GeoPoint? {
  val lat = obj.optDouble("lat", Double.NaN)
  val lon = obj.optDouble("lon", Double.NaN)
  return if (!lat.isNaN() && !lon.isNaN()) GeoPoint(lat, lon) else null
}

/**
 * Degraded-geometry fallback: reuse the stored point coordinates when they
 * exist; when none were persisted the shard cannot be located honestly, so
 * null DROPS the event instead of fabricating a location for it.
 */
private fun degradedGeometry(obj: JSONObject): EventGeometry? {
  val stored = storedPoint(obj) ?: return null
  return EventGeometry.Point(stored.lat, stored.lon)
}

internal fun serializeDetails(details: EventDetails): JSONObject = when (details) {
  is EventDetails.Quake -> JSONObject()
    .put(KIND, DETAILS_QUAKE)
    .put("magnitude", details.magnitude)
    .put("depthKm", details.depthKm)
    .put("place", details.place)
    .put("url", details.url ?: "")

  is EventDetails.Fire -> JSONObject()
    .put(KIND, DETAILS_FIRE)
    .put("satellite", details.satellite)
    .put("instrument", details.instrument)
    .put("frpMegawatts", details.frpMegawatts ?: JSONObject.NULL)
    .put("dayNight", details.dayNight ?: "")

  is EventDetails.OfficialAlert -> JSONObject()
    .put(KIND, DETAILS_OFFICIAL_ALERT)
    .put("event", details.event)
    .put("urgency", details.urgency)
    .put("certainty", details.certainty)
    .put("senderName", details.senderName)
    .put("instruction", details.instruction ?: "")
    .put("webLink", details.webLink ?: "")

  is EventDetails.UserIncident -> JSONObject()
    .put(KIND, DETAILS_USER_INCIDENT)
    .put("categoryLabel", details.categoryLabel)
    .put("reporterNote", details.reporterNote)

  EventDetails.Generic -> JSONObject().put(KIND, DETAILS_GENERIC)
}

internal fun deserializeDetails(obj: JSONObject?): EventDetails {
  if (obj == null) return EventDetails.Generic
  return when (obj.optString(KIND)) {
    DETAILS_QUAKE -> EventDetails.Quake(
      magnitude = obj.optDouble("magnitude", Double.NaN),
      depthKm = obj.optDouble("depthKm", 0.0),
      place = obj.optString("place"),
      url = obj.optString("url").takeIf { it.isNotBlank() }
    )
    DETAILS_FIRE -> EventDetails.Fire(
      satellite = obj.optString("satellite"),
      instrument = obj.optString("instrument"),
      frpMegawatts = obj.optDouble("frpMegawatts", Double.NaN).takeIf { !it.isNaN() },
      dayNight = obj.optString("dayNight").takeIf { it.isNotBlank() }
    )
    DETAILS_OFFICIAL_ALERT -> EventDetails.OfficialAlert(
      event = obj.optString("event"),
      urgency = obj.optString("urgency"),
      certainty = obj.optString("certainty"),
      senderName = obj.optString("senderName"),
      instruction = obj.optString("instruction").takeIf { it.isNotBlank() },
      webLink = obj.optString("webLink").takeIf { it.isNotBlank() }
    )
    DETAILS_USER_INCIDENT -> EventDetails.UserIncident(
      categoryLabel = obj.optString("categoryLabel"),
      reporterNote = obj.optString("reporterNote")
    )
    else -> EventDetails.Generic
  }
}

/**
 * Backward compatibility: shards written before details serialization existed
 * carry a magnitude field on the event root (written by the original broken
 * attempt) or nothing at all. Recover magnitude for earthquakes; otherwise the
 * event keeps an honest Generic payload.
 */
internal fun legacyDetails(obj: JSONObject, type: DisasterType): EventDetails {
  if (obj.has("details")) return deserializeDetails(obj.optJSONObject("details"))
  val magnitude = obj.optDouble("magnitude", Double.NaN)
  if (type == DisasterType.EARTHQUAKE && !magnitude.isNaN()) {
    return EventDetails.Quake(
      magnitude = magnitude,
      depthKm = obj.optDouble("depthKm", 0.0),
      place = obj.optString("title").ifBlank { "Location not provided by source" },
      url = obj.optString("url").takeIf { it.isNotBlank() }
    )
  }
  return EventDetails.Generic
}

private const val KIND = "kind"
private const val DETAILS_QUAKE = "QUAKE"
private const val DETAILS_FIRE = "FIRE"
private const val DETAILS_OFFICIAL_ALERT = "OFFICIAL_ALERT"
private const val DETAILS_USER_INCIDENT = "USER_INCIDENT"
private const val DETAILS_GENERIC = "GENERIC"

