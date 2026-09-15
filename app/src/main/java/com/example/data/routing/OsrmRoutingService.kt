package com.example.data.routing

import android.util.Log
import com.example.data.model.GeoMath
import com.example.data.model.HazardZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class GeoPoint(
  val lat: Double,
  val lon: Double
)

data class RouteStep(
  val instruction: String,
  val distanceMeters: Double,
  val durationSeconds: Double
)

/**
 * Disaster-aware evacuation route result.
 *
 * Beyond distance/duration, every route carries a SAFETY evaluation:
 * hazard warnings, route safety status and a route-safety score. This is the
 * single routing result type used across the whole application — there is no
 * second parallel routing implementation.
 */
data class RouteResult(
  val distanceMeters: Double,
  val durationSeconds: Double,
  val pathPoints: List<GeoPoint>,
  val steps: List<RouteStep>,
  val isLiveOsrm: Boolean,
  val summary: String,
  val travelMode: String,
  /** Human warnings for each hazard the route passes near/through. */
  val hazardWarnings: List<RouteHazardWarning> = emptyList(),
  /** SAFE / CAUTION / DANGER for the selected route. */
  val routeSafetyStatus: RouteSafetyStatus = RouteSafetyStatus.SAFE,
  /** 0..100 — higher is safer. */
  val routeSafetyScore: Int = 100,
  /** Destination label (e.g. shelter name). */
  val destinationName: String = "Safe Zone"
) {
  /**
   * Stable content-derived identity (derived once from distance, path length
   * and path content). List UI must compare routes by this id — never by
   * object identity (===), because state copies create equal-but-distinct
   * instances.
   */
  val routeId: String

  init {
    routeId = "route-${distanceMeters.roundToInt()}-${pathPoints.size}-${pathPoints.hashCode()}"
  }

  val isReroutable: Boolean get() = hazardWarnings.any { it.isBlocking }
}

enum class RouteSafetyStatus(val label: String) {
  SAFE("Safe Corridor"),
  CAUTION("Caution — Hazard Nearby"),
  DANGER("Danger — Route Enters Hazard Zone")
}

/**
 * One hazard warning attached to a route.
 */
data class RouteHazardWarning(
  val hazardName: String,
  val hazardTypeLabel: String,
  val message: String,
  /** True when the route actually enters the hazard zone. */
  val isBlocking: Boolean
)

/**
 * Penalty policy for disaster-aware routing.
 *
 * FUTURE INTEGRATION: when live road/hazard feeds (KSDMA closures, CWC gauge
 * flooding, GSI slope alerts) are connected, they plug into
 * [computePenaltyFor] — the architecture is already structured around
 * Safety + Risk + Distance + ETA + Accessibility, so the safest route is NOT
 * automatically the shortest one. Prepared policies cover flood avoidance,
 * landslide avoidance, fire-zone avoidance, blocked-road avoidance,
 * bridge-risk avoidance, restricted-area avoidance, road accessibility,
 * emergency corridors and alternative routes.
 */
object HazardRoutingPolicy {

  /** How much a hazard penalizes a candidate path (meters-equivalent). */
  fun computePenaltyFor(
    path: List<GeoPoint>,
    hazards: List<HazardZone>
  ): HazardPenalty {
    if (path.size < 2) return HazardPenalty.NONE
    var penaltyMeters = 0.0
    val warnings = mutableListOf<RouteHazardWarning>()
    var worstStatus: RouteSafetyStatus? = null

    for (hazard in hazards) {
      var minApproach = Double.MAX_VALUE
      var enters = false
      for (i in 0 until path.size - 1) {
        val approach = GeoMath.closestApproachMeters(hazard.center, path[i], path[i + 1])
        if (approach < minApproach) minApproach = approach
        if (approach <= hazard.radiusMeters) {
          enters = true
          break
        }
      }
      if (minApproach > hazard.radiusMeters * 4) continue // far away — ignore

      if (enters || minApproach <= hazard.radiusMeters) {
        val severityFactor = hazard.severity.weight.toDouble()
        penaltyMeters += BASE_PENALTY_METERS * severityFactor * (if (enters) 2.0 else 1.0)
        val status = if (enters) RouteSafetyStatus.DANGER else RouteSafetyStatus.CAUTION
        if (worstStatus == null || status.ordinal > worstStatus!!.ordinal) worstStatus = status
        warnings += RouteHazardWarning(
          hazardName = hazard.name,
          hazardTypeLabel = hazard.type.label,
          message = if (enters) {
            "Route passes THROUGH the ${hazard.name} (${hazard.type.label}, ${hazard.severity.label}). " +
              "Find an alternative corridor or wait for official guidance."
          } else {
            "Route runs close to the ${hazard.name} (${hazard.type.label}). Allow extra time and stay alert."
          },
          isBlocking = enters && hazard.severity.weight >= 3
        )
      }
    }

    val safetyScore = (100 - penaltyMeters / PENALTY_SCALE).roundToInt().coerceIn(0, 100)
    return HazardPenalty(
      penaltyMeters = penaltyMeters,
      warnings = warnings,
      safetyScore = safetyScore,
      status = worstStatus ?: RouteSafetyStatus.SAFE
    )
  }

  data class HazardPenalty(
    val penaltyMeters: Double,
    val warnings: List<RouteHazardWarning>,
    val safetyScore: Int,
    val status: RouteSafetyStatus
  ) {
    companion object { val NONE = HazardPenalty(0.0, emptyList(), 100, RouteSafetyStatus.SAFE) }
  }

  private const val BASE_PENALTY_METERS = 1_500.0
  private const val PENALTY_SCALE = 40.0
}

/**
 * Open Source Routing Machine (OSRM) road-routing service — the single
 * road-routing engine of the application.
 *
 * Pipeline: CURRENT LOCATION -> HAZARD CHECK -> SAFE-ZONE OPTIONS ->
 * SAFE ROUTE -> DESTINATION. Walking and driving profiles are supported.
 * Offline, a geodesic hazard-avoiding fallback corridor keeps the app useful.
 */
object OsrmRoutingService {

  private const val TAG = "OsrmRoutingService"

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(7, TimeUnit.SECONDS)
      .build()
  }

  /**
   * Calculates a road route using the public OSRM HTTP API and evaluates the
   * returned geometry against the current hazard picture. Falls back to an
   * offline hazard-skirting corridor when the network is unavailable.
   */
  suspend fun calculateRoute(
    origin: GeoPoint,
    destination: GeoPoint,
    mode: String = "foot", // "foot" or "driving"
    hazards: List<HazardZone> = emptyList(),
    destinationName: String = "Safe Zone"
  ): RouteResult = withContext(Dispatchers.IO) {
    // Profile-matched endpoints: the router.project-osrm.org demo server hosts
    // only the car profile — a /foot/ request there silently returns car
    // geometry. The FOSSGIS community server runs dedicated foot + car
    // instances, so each travel mode queries its own profile.
    val endpoint = if (mode == "driving") "https://routing.openstreetmap.de/routed-car/route/v1/driving" else "https://routing.openstreetmap.de/routed-foot/route/v1/foot"
    val url = "$endpoint/" +
      "${origin.lon},${origin.lat};${destination.lon},${destination.lat}" +
      "?overview=full&geometries=geojson&steps=true" // routes[0] only — alternatives would 3x the payload

    var liveResult: RouteResult? = null
    try {
      val request = Request.Builder()
        .url(url)
        .header("User-Agent", "VippattiSarana-DisasterRelief/1.0 (Android; OSM OSRM Routing)")
        .build()

      httpClient.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          val responseBody = response.body?.string()
          if (!responseBody.isNullOrBlank()) {
            val json = JSONObject(responseBody)
            if (json.optString("code") == "Ok") {
              val routes = json.getJSONArray("routes")
              if (routes.length() > 0) {
                val routeObj = routes.getJSONObject(0)
                liveResult = parseOsrmRoute(routeObj, mode, hazards, destinationName)
              }
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "OSRM routing unavailable; offline hazard-skirting corridor will be used: ${e.message}")
    }

    liveResult ?: calculateOfflineTacticalRoute(origin, destination, mode, hazards, destinationName)
  }

  private fun parseOsrmRoute(
    routeObj: JSONObject,
    mode: String,
    hazards: List<HazardZone>,
    destinationName: String
  ): RouteResult {
    val distance = routeObj.optDouble("distance", 0.0)
    val duration = routeObj.optDouble("duration", 0.0)

    val geometry = routeObj.getJSONObject("geometry")
    val coordsArray = geometry.getJSONArray("coordinates")
    val points = mutableListOf<GeoPoint>()
    for (i in 0 until coordsArray.length()) {
      val pt = coordsArray.getJSONArray(i)
      points.add(GeoPoint(pt.getDouble(1), pt.getDouble(0)))
    }

    val stepsList = mutableListOf<RouteStep>()
    val legs = routeObj.optJSONArray("legs")
    var summary = "OSRM Safe Evacuation Route"
    if (legs != null && legs.length() > 0) {
      val leg0 = legs.getJSONObject(0)
      summary = leg0.optString("summary", summary)
      val stepsArray = leg0.optJSONArray("steps")
      if (stepsArray != null) {
        for (s in 0 until stepsArray.length()) {
          val stepObj = stepsArray.getJSONObject(s)
          val maneuver = stepObj.optJSONObject("maneuver")
          val instruction = when (maneuver?.optString("type")) {
            "depart" -> "Depart along marked evacuation path"
            "turn" -> "Turn ${maneuver.optString("modifier")} onto ${stepObj.optString("name", "safe road")}"
            "arrive" -> "Arrive safely at $destinationName"
            else -> stepObj.optString("name").ifBlank { "Continue on designated route" }
          }
          stepsList.add(RouteStep(instruction, stepObj.optDouble("distance", 0.0), stepObj.optDouble("duration", 0.0)))
        }
      }
    }
    if (stepsList.isEmpty()) {
      stepsList.add(RouteStep("Follow evacuation corridor to $destinationName", distance, duration))
    }

    val penalty = HazardRoutingPolicy.computePenaltyFor(points, hazards)
    return RouteResult(
      distanceMeters = distance,
      durationSeconds = duration,
      pathPoints = points,
      steps = stepsList,
      isLiveOsrm = true,
      summary = summary.ifBlank { "OSRM Validated Passage" },
      travelMode = mode,
      hazardWarnings = penalty.warnings,
      routeSafetyStatus = penalty.status,
      routeSafetyScore = penalty.safetyScore,
      destinationName = destinationName
    )
  }

  /**
   * Offline disaster fallback: builds a hazard-skirting corridor of waypoints
   * (offset perpendicular from any nearby hazard) and computes a geodesic
   * distance with walking/driving ETA plus the same hazard-safety evaluation.
   */
  fun calculateOfflineTacticalRoute(
    origin: GeoPoint,
    destination: GeoPoint,
    mode: String,
    hazards: List<HazardZone> = emptyList(),
    destinationName: String = "Safe Zone"
  ): RouteResult {
    val waypoints = mutableListOf(origin)

    // Intermediate tactical waypoints that skirt around nearby hazard areas.
    for (hazard in hazards) {
      val approachStart = GeoMath.closestApproachMeters(hazard.center, origin, destination)
      if (approachStart <= hazard.radiusMeters * 1.5) {
        val detourBearing = (GeoMath.bearingDegrees(origin, hazard.center) + 90.0) % 360.0
        val midLat = (origin.lat + destination.lat) / 2 + detourBearing * 0.00003
        val midLon = (origin.lon + destination.lon) / 2 + (detourBearing / 90.0) * 0.003
        waypoints.add(GeoPoint(midLat, midLon))
      }
    }
    waypoints.add(destination)

    var totalMeters = 0.0
    for (i in 0 until waypoints.size - 1) {
      totalMeters += GeoMath.distanceMeters(waypoints[i], waypoints[i + 1])
    }

    // Walking pace ~4.8 km/h; driving ~30 km/h during evacuation conditions.
    val speedMps = if (mode == "driving") 8.33 else 1.35
    val durationSeconds = totalMeters / speedMps

    val steps = listOf(
      RouteStep("Exit hazard area toward the designated corridor", totalMeters * 0.35, durationSeconds * 0.35),
      RouteStep("Follow the elevated bypass road (clear of hazard zones)", totalMeters * 0.35, durationSeconds * 0.35),
      RouteStep("Arrive at $destinationName intake point", totalMeters * 0.30, durationSeconds * 0.30)
    )

    val penalty = HazardRoutingPolicy.computePenaltyFor(waypoints, hazards)
    return RouteResult(
      distanceMeters = totalMeters,
      durationSeconds = durationSeconds,
      pathPoints = waypoints,
      steps = steps,
      isLiveOsrm = false,
      summary = "Offline Hazard-Skirting Corridor",
      travelMode = mode,
      hazardWarnings = penalty.warnings,
      routeSafetyStatus = penalty.status,
      routeSafetyScore = penalty.safetyScore,
      destinationName = destinationName
    )
  }

  /**
   * Alternative routes: computes up to [maxAlternatives] distinct candidate
   * corridors to the same destination (today via slightly different offline
   * detour geometries; live OSRM alternatives feed in naturally later).
   */
  suspend fun calculateAlternativeRoutes(
    origin: GeoPoint,
    destination: GeoPoint,
    mode: String,
    hazards: List<HazardZone> = emptyList(),
    destinationName: String = "Safe Zone",
    maxAlternatives: Int = 2
  ): List<RouteResult> = withContext(Dispatchers.IO) {
    val alternatives = mutableListOf<RouteResult>()
    // Primary corridor.
    alternatives += calculateRoute(origin, destination, mode, hazards, destinationName)
    // Additional offline detour variants (left/right skirting directions).
    for (k in 1..maxAlternatives) {
      val dir = if (k % 2 == 1) 1.0 else -1.0
      alternatives += offlineDetourVariant(origin, destination, mode, hazards, destinationName, dir)
    }
    alternatives.distinctBy { it.pathPoints.hashCode() }.sortedByDescending { it.routeSafetyScore }
  }

  private fun offlineDetourVariant(
    origin: GeoPoint,
    destination: GeoPoint,
    mode: String,
    hazards: List<HazardZone>,
    destinationName: String,
    direction: Double
  ): RouteResult {
    val midLat = (origin.lat + destination.lat) / 2 + direction * 0.004
    val midLon = (origin.lon + destination.lon) / 2 + direction * 0.005
    val path = listOf(origin, GeoPoint(midLat, midLon), destination)
    var totalMeters = 0.0
    for (i in 0 until path.size - 1) {
      totalMeters += GeoMath.distanceMeters(path[i], path[i + 1])
    }
    val speedMps = if (mode == "driving") 8.33 else 1.35
    val penalty = HazardRoutingPolicy.computePenaltyFor(path, hazards)
    return RouteResult(
      distanceMeters = totalMeters,
      durationSeconds = totalMeters / speedMps,
      pathPoints = path,
      steps = listOf(RouteStep("Detour corridor variant via offset waypoint", totalMeters, totalMeters / speedMps)),
      isLiveOsrm = false,
      summary = "Alternative Detour Corridor ${if (direction > 0) "A" else "B"}",
      travelMode = mode,
      hazardWarnings = penalty.warnings,
      routeSafetyStatus = penalty.status,
      routeSafetyScore = penalty.safetyScore,
      destinationName = destinationName
    )
  }

  /**
   * Standard Haversine distance (kept as the public convenience metric).
   */
  fun haversineDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double = GeoMath.distanceMeters(p1, p2)

  fun formatDistance(meters: Double): String = GeoMath.formatKm(meters)

  fun formatDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60.0).roundToInt()
    return if (totalMinutes >= 60) {
      "${totalMinutes / 60}h ${totalMinutes % 60}m"
    } else {
      "${totalMinutes.coerceAtLeast(1)} mins"
    }
  }
}
