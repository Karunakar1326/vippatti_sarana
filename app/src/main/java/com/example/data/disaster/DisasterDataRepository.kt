package com.example.data.disaster

import com.example.data.model.GeoMath
import com.example.data.model.HazardZone
import com.example.data.routing.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Per-provider state surfaced to the UI (freshness label + last update time
 * + honest status, e.g. "Live source unavailable").
 */
data class ProviderState(
  val source: DisasterSource,
  val eventCount: Int,
  val fetchedAtMillis: Long,
  val isFromCache: Boolean,
  val isLive: Boolean,
  val statusMessage: String?
) {
  val freshnessLabel: String
    get() = when {
      fetchedAtMillis <= 0L -> "Unavailable"
      isLive -> "Live"
      isFromCache -> "Cached"
      else -> "Recent"
    }
}

/** Full repository snapshot consumed by the ViewModel + map renderer. */
data class DisasterFeed(
  val events: List<DisasterEvent>,
  val providerStates: List<ProviderState>,
  val userReports: List<DisasterEvent>,
  val isAnyLive: Boolean
)

/**
 * ============================================================================
 * DISASTER DATA REPOSITORY — the single data layer between providers and UI.
 * ============================================================================
 *
 * Pipeline per provider:
 *   fetch -> validate -> dedupe (source event id) -> drop expired ->
 *   write cache -> emit; offline: serve the last valid cached shard.
 *
 * LIVE vs DEMO: the repository only ever holds REAL fetched events (plus
 * explicitly submitted user reports). Pilot/demo hazards are NEVER mixed in
 * here — they remain a separate, clearly labeled concept owned by the
 * ViewModel (Demo Mode).
 */
class DisasterDataRepository(
  private val providers: List<DisasterDataProvider>,
  private val cache: DisasterCache,
  private val clock: () -> Long = System::currentTimeMillis
) {

  /** Manual sync: query every provider, update cache, return the merged feed. */
  suspend fun refresh(): DisasterFeed {
    val now = clock()
    val states = mutableListOf<ProviderState>()
    val allEvents = mutableListOf<DisasterEvent>()
    for (provider in providers) {
      when (val result = provider.fetchIndiaEvents()) {
        is ProviderResult.Success -> {
          val valid = result.events
            .filter { it.isValid(now) }
            .dedupeBySourceEventId()
          withContext(Dispatchers.IO) {
            cache.write(
              provider.providerId,
              CachedProviderFeed(valid, result.fetchedAtMillis, null)
            )
          }
          allEvents += valid
          states += ProviderState(
            source = provider.providerId,
            eventCount = valid.size,
            fetchedAtMillis = result.fetchedAtMillis,
            isFromCache = false,
            isLive = DisasterCachePolicy.isFresh(result.fetchedAtMillis, now),
            statusMessage = null
          )
        }
        is ProviderResult.Failure -> {
          // Offline/auth failure: keep the last valid cached shard (if usable).
          val cached = withContext(Dispatchers.IO) { cache.read(provider.providerId) }
          val usable = cached
            ?.takeIf { DisasterCachePolicy.isUsable(it.fetchedAtMillis, now) }
            ?.let { feed ->
              CachedProviderFeed(
                feed.events.filter { it.isValid(now) }.dedupeBySourceEventId(),
                feed.fetchedAtMillis,
                feed.statusMessage
              )
            }
          usable?.let { allEvents += it.events }
          states += ProviderState(
            source = provider.providerId,
            eventCount = usable?.events?.size ?: 0,
            fetchedAtMillis = usable?.fetchedAtMillis ?: 0L,
            isFromCache = usable != null,
            isLive = false,
            statusMessage = result.reason
          )
        }
      }
    }
    return DisasterFeed(
      events = allEvents.dedupeBySourceEventId(),
      providerStates = states,
      userReports = emptyList(),
      isAnyLive = states.any { it.isLive }
    )
  }

  /**
   * Cold start: serve usable cached shards instantly (offline survival); a
   * fresh (< 15 min) cache avoids network entirely (quota/battery friendly).
   * Returns null when nothing usable is cached — the ViewModel then fetches.
   */
  suspend fun loadCachedOnly(): DisasterFeed? {
    val now = clock()
    val states = mutableListOf<ProviderState>()
    val allEvents = mutableListOf<DisasterEvent>()
    var anyFresh = false
    for (provider in providers) {
      val cached = withContext(Dispatchers.IO) { cache.read(provider.providerId) }
      val usable = cached?.takeIf { DisasterCachePolicy.isUsable(it.fetchedAtMillis, now) }
      if (usable != null) {
        val valid = usable.events.filter { it.isValid(now) }.dedupeBySourceEventId()
        allEvents += valid
        val fresh = DisasterCachePolicy.isFresh(usable.fetchedAtMillis, now)
        anyFresh = anyFresh || fresh
        states += ProviderState(
          source = provider.providerId,
          eventCount = valid.size,
          fetchedAtMillis = usable.fetchedAtMillis,
          isFromCache = true,
          isLive = fresh,
          statusMessage = null
        )
      }
    }
    if (states.isEmpty()) return null
    return DisasterFeed(
      events = allEvents.dedupeBySourceEventId(),
      providerStates = states,
      userReports = emptyList(),
      isAnyLive = anyFresh
    )
  }
}

// ============================================================================
// Pure pipeline operations (top-level so any consumer can use them directly).
// ============================================================================

/** Event is valid when not expired and inside the fetch honesty window. */
fun DisasterEvent.isValid(nowMillis: Long): Boolean {
  if (status == EventStatus.EXPIRED) return false
  if (expiresAtMillis != null && expiresAtMillis <= nowMillis) return false
  if (observedAtMillis <= 0L) return false
  // Events older than the cache max age are not actionable intelligence.
  return nowMillis - observedAtMillis <= DisasterCachePolicy.MAX_AGE_MILLIS
}

/** Dedupe by source + source event id, newest first. */
fun List<DisasterEvent>.dedupeBySourceEventId(): List<DisasterEvent> {
  val seen = HashSet<String>()
  val out = mutableListOf<DisasterEvent>()
  for (event in sortedByDescending { it.updatedAtMillis }) {
    if (event.dedupeKey in seen) continue
    seen += event.dedupeKey
    out += event
  }
  return out
}

/** Hazard zones for the risk/evaluator/routing engines (live events only). */
fun toHazardZones(events: List<DisasterEvent>): List<HazardZone> =
  events.mapNotNull { DisasterEventNormalizer.toHazardZone(it) }

/** Bounding-box query over in-memory events (map geographic filtering). */
fun List<DisasterEvent>.inBounds(
  minLat: Double, minLon: Double, maxLat: Double, maxLon: Double
): List<DisasterEvent> = filter { event ->
  when (val g = event.geometry) {
    is EventGeometry.Point -> g.lat in minLat..maxLat && g.lon in minLon..maxLon
    is EventGeometry.MultiPoint -> g.points.any {
      it.lat in minLat..maxLat && it.lon in minLon..maxLon
    }
    is EventGeometry.Line -> g.points.any {
      it.lat in minLat..maxLat && it.lon in minLon..maxLon
    }
    is EventGeometry.Polygon -> g.ring.any {
      it.lat in minLat..maxLat && it.lon in minLon..maxLon
    }
    is EventGeometry.RasterLayer -> false
  }
}

/** Radius query over in-memory events (local analysis around the user). */
fun List<DisasterEvent>.withinRadius(center: GeoPoint, radiusMeters: Double): List<DisasterEvent> =
  filter { event ->
    val point = when (val g = event.geometry) {
      is EventGeometry.Point -> GeoPoint(g.lat, g.lon)
      is EventGeometry.MultiPoint -> g.points.firstOrNull() ?: return@filter false
      is EventGeometry.Line -> g.points.firstOrNull() ?: return@filter false
      is EventGeometry.Polygon -> g.ring.firstOrNull() ?: return@filter false
      is EventGeometry.RasterLayer -> return@filter false
    }
    GeoMath.distanceMeters(center, point) <= radiusMeters
  }

