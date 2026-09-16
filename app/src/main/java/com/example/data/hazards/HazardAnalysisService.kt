package com.example.data.hazards

import com.example.data.model.GeoMath
import com.example.data.model.HazardZone
import com.example.data.model.HazardType
import com.example.data.routing.GeoPoint

/**
 * Result of analyzing hazards around a single point (usually the user).
 */
data class HazardExposure(
  val hazard: HazardZone,
  /** Distance in meters from the point to the hazard center (negative inside). */
  val distanceToCenterMeters: Double,
  val isInsideZone: Boolean
)

/**
 * Pure hazard analysis over the India-network hazard dataset.
 *
 * FUTURE INTEGRATION: when live feeds (IMD rainfall, KSDMA alerts, NRSC flood
 * extents, CWC river gauges, GSI landslide warnings) are connected, only the
 * input list changes — every consumer (risk engine, evaluator, routing
 * penalties, map overlays) already speaks HazardZone.
 */
object HazardAnalysisService {

  /**
   * All hazards affecting [point]: a hazard affects the point when the point
   * lies inside the hazard radius (or within the safety margin of its edge).
   */
  fun affectingHazards(point: GeoPoint, hazards: List<HazardZone>): List<HazardExposure> =
    hazards.map { hazard ->
      val d = GeoMath.distanceMeters(point, hazard.center)
      HazardExposure(
        hazard = hazard,
        distanceToCenterMeters = d,
        isInsideZone = d <= hazard.radiusMeters
      )
    }.filter { it.isInsideZone }

  /**
   * Nearest hazard of any type — used for GREEN risk explanations
   * ("no active hazard within X km").
   */
  fun nearestHazard(point: GeoPoint, hazards: List<HazardZone>): HazardExposure? {
    if (hazards.isEmpty()) return null
    return hazards
      .map { h ->
        val d = GeoMath.distanceMeters(point, h.center)
        HazardExposure(h, d, d <= h.radiusMeters)
      }
      .minByOrNull { it.distanceToCenterMeters }
  }

  /**
   * Maximum severity weight among hazards affecting the point; 0 when safe.
   */
  fun maxSeverityWeight(point: GeoPoint, hazards: List<HazardZone>): Int =
    affectingHazards(point, hazards).maxOfOrNull { it.hazard.severity.weight } ?: 0

  /**
   * Hazards of a specific type affecting the point (e.g. all flood areas).
   */
  fun hazardsOfType(type: HazardType, hazards: List<HazardZone>): List<HazardZone> =
    hazards.filter { it.type == type }

  /**
   * True when [point] sits inside any flood zone — drives "move to higher
   * ground" and "move away from river" recommendations.
   */
  fun isInsideFloodZone(point: GeoPoint, hazards: List<HazardZone>): Boolean =
    affectingHazards(point, hazards).any { it.hazard.type == HazardType.FLOOD }

  /**
   * Counts distinct hazard types affecting the point — used in risk scoring
   * (multiple simultaneous hazards escalate the level).
   */
  fun distinctHazardTypeCount(point: GeoPoint, hazards: List<HazardZone>): Int =
    affectingHazards(point, hazards).map { it.hazard.type }.distinct().size
}
