package com.example.data.risk

import com.example.data.hazards.HazardAnalysisService
import com.example.data.model.GeoMath
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardZone
import com.example.data.routing.GeoPoint

/**
 * Personal risk level — RED / ORANGE / YELLOW / GREEN.
 */
enum class RiskLevel(val label: String) {
  RED("RED"), ORANGE("ORANGE"), YELLOW("YELLOW"), GREEN("GREEN")
}

/**
 * Personal risk intelligence for the user's current location.
 */
data class PersonalRiskAssessment(
  val location: GeoPoint,
  val level: RiskLevel,
  /** Primary hazard affecting the user (null when none). */
  val primaryHazard: HazardZone?,
  val affectingHazardCount: Int,
  /** Understandable explanation, e.g. "High flood risk because the current
   * location is within the affected flood zone." */
  val explanation: String,
  /** Short risk trend summary, e.g. "Worsening — rainfall cell intensifying". */
  val trendSummary: String,
  /** Confidence/status line about data provenance. */
  val confidenceNote: String
)

/**
 * Pure personal-risk engine. The location flows in from REAL hardware GPS when
 * available (or the clearly-labeled India fallback), and hazards flow in from
 * the current India-wide set (live provider feeds + user reports + the
 * labeled mock network while it is toggled on).
 */
object RiskAssessmentEngine {

  fun assess(
    location: GeoPoint,
    hazards: List<HazardZone>,
    provenanceNote: String
  ): PersonalRiskAssessment {
    val exposures = HazardAnalysisService.affectingHazards(location, hazards)
    val primary = exposures.maxByOrNull { it.hazard.severity.weight }?.hazard
    val count = exposures.size

    val level = when {
      primary == null -> RiskLevel.GREEN
      primary.severity == HazardSeverity.EXTREME -> RiskLevel.RED
      primary.severity == HazardSeverity.HIGH -> if (count >= 2) RiskLevel.RED else RiskLevel.ORANGE
      primary.severity == HazardSeverity.MODERATE -> RiskLevel.ORANGE
      else -> RiskLevel.YELLOW
    }

    val explanation = when {
      primary == null -> {
        val nearest = HazardAnalysisService.nearestHazard(location, hazards)
        if (nearest != null) {
          "No active hazard covers your location. Nearest watched area is " +
            "${primary?.name ?: nearest.hazard.name} about ${GeoMath.formatKm(nearest.distanceToCenterMeters)} away."
        } else {
          "No active hazard covers your location on the current hazard picture."
        }
      }
      else -> "${primary.severity.label} ${primary.type.label.lowercase()} risk because your current " +
        "location is within the affected ${primary.name.lowercase()}."
    }

    val trendSummary = when {
      primary == null -> "Stable — no hazard areas cover your location."
      primary.trend == HazardTrend.WORSENING ->
        "Worsening — ${primary.name.lowercase()} is intensifying."
      primary.trend == HazardTrend.IMPROVING ->
        "Improving — ${primary.name.lowercase()} is subsiding."
      else -> "Steady — ${primary.name.lowercase()} is unchanged."
    }

    return PersonalRiskAssessment(
      location = location,
      level = level,
      primaryHazard = primary,
      affectingHazardCount = count,
      explanation = explanation,
      trendSummary = trendSummary,
      confidenceNote = provenanceNote
    )
  }
}
