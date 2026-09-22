package com.example.data.risk

import com.example.data.model.GeoMath
import com.example.data.model.HazardZone
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

  }
