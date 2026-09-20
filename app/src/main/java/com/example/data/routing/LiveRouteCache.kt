package com.example.data.routing

import com.example.data.model.HazardZone
import kotlin.math.roundToInt

/**
 * In-memory cache of LIVE OSRM road routes so repeat views render the exact
 * road pathway instantly instead of flashing the straight preview corridor
 * while the network re-answers.
 *
 * The key binds destination + travel mode + origin grid (~500 m cells) +
 * hazard picture: moving to another grid cell, switching mode, picking
 * another shelter, or any change in the hazard set is a cache miss and the
 * caller revalidates over the network. Entries hold live road geometry only.
 */
class LiveRouteCache(private val maxEntries: Int = 30) {

  private val map = LinkedHashMap<String, RouteResult>()

  @Synchronized fun get(key: String): RouteResult? = map[key]

  @Synchronized fun put(key: String, route: RouteResult) {
    if (!route.isLiveOsrm || route.pathPoints.isEmpty()) return
    map.remove(key)
    map[key] = route
    while (map.size > maxEntries) {
      map.remove(map.keys.first())
    }
  }

  @Synchronized fun clear() = map.clear()

  @Synchronized fun size(): Int = map.size

  companion object {
    /** Origin grid cell size in degrees (~500 m) — jitter inside a cell reuses. */
    private const val GRID_DEGREES = 0.005

    fun key(
      zoneId: String,
      mode: String,
      origin: GeoPoint,
      hazards: List<HazardZone>
    ): String {
      val gridLat = (origin.lat / GRID_DEGREES).roundToInt()
      val gridLon = (origin.lon / GRID_DEGREES).roundToInt()
      val hazardSig = hazards.map { it.id }.sorted().hashCode()
      return "$zoneId|$mode|$gridLat|$gridLon|$hazardSig"
    }
  }
}
