package com.example.data.risk

import com.example.data.model.SafeZone
import com.example.data.shelters.SafeZoneEvaluation
import com.example.data.shelters.ShelterCapacityService

/**
 * Household member category used for relocation priority. Population counts
 * are NOT fabricated — only the categories and priorities are defined; real
 * household/village data is entered or connected later.
 */
data class VulnerableCategory(
  val id: String,
  val label: String,
  val priorityWeight: Int,
  val supportNeeds: String
)

/**
 * Structured relocation plan for one household/location.
 */
data class RelocationPlan(
  val priorityBand: String,
  val priorityScore: Int,
  val bandExplanation: String,
  val assignedShelter: SafeZoneEvaluation?,
  val vulnerableCategories: List<VulnerableCategory>,
  val overflowNote: String?
)

/**
 * Relocation intelligence:
 *   Current habitation/location -> risk assessment -> evacuation priority ->
 *   safe-zone assignment -> capacity check -> route.
 *
 * Supports household, village/ward and vulnerable-population priority WITHOUT
 * fabricating population statistics — the data structure is ready for
 * verified datasets (census ward data, KSDMA registries).
 */
object RelocationPlanner {

  /** Standard vulnerable categories for Indian disaster relocation. */
  val VULNERABLE_CATEGORIES: List<VulnerableCategory> = listOf(
    VulnerableCategory("elderly", "Elderly (60+)", 3, "Mobility assistance • priority seating"),
    VulnerableCategory("children", "Children under 12", 3, "Guardian tracking • child-safe areas"),
    VulnerableCategory("disabled", "Persons with disabilities", 4, "Wheelchair access • caregiver support"),
    VulnerableCategory("pregnant", "Pregnant women", 4, "Medical monitoring • priority medical"),
    VulnerableCategory("medical", "Medical dependency", 4, "Power for devices • medicine storage")
  )

  /**
   * Evacuation priority for a household, derived from risk level + vulnerable
   * members. Higher score = earlier evacuation window.
   */
  fun householdPriority(
    risk: PersonalRiskAssessment,
    vulnerableCategoryIds: Set<String>
  ): Pair<String, Int> {
    val riskWeight = when (risk.level) {
      RiskLevel.RED -> 50
      RiskLevel.ORANGE -> 30
      RiskLevel.YELLOW -> 15
      RiskLevel.GREEN -> 5
    }
    val vulnerableWeight = vulnerableCategoryIds.sumOf { id ->
      VULNERABLE_CATEGORIES.firstOrNull { it.id == id }?.priorityWeight ?: 0
    }
    val score = (riskWeight + vulnerableWeight).coerceAtMost(70)
    val band = when {
      score >= 55 -> "IMMEDIATE PRIORITY"
      score >= 35 -> "HIGH PRIORITY"
      score >= 20 -> "ELEVATED PRIORITY"
      else -> "STANDARD PRIORITY"
    }
    return band to score
  }

  /**
   * Build the full relocation plan: priority band, shelter assignment
   * (best-ranked feasible shelter), capacity check and overflow fallback.
   */
  fun plan(
    risk: PersonalRiskAssessment,
    rankedShelters: List<SafeZoneEvaluation>,
    vulnerableCategoryIds: Set<String>
  ): RelocationPlan {
    val (band, score) = householdPriority(risk, vulnerableCategoryIds)
    val assignment = rankedShelters.firstOrNull()
    val overflow = assignment?.let { best ->
      ShelterCapacityService.overflowRecommendation(
        primary = best.zone,
        zones = rankedShelters.map { it.zone },
        distanceTo = { candidate ->
          rankedShelters.firstOrNull { it.zone.id == candidate.id }?.distanceMeters ?: 0.0
        }
      )
    }

    val bandExplanation = buildString {
      append("Priority ${band.lowercase()} (score $score) — ")
      append(
        when (risk.level) {
          RiskLevel.RED -> "current location is inside an active hazard area"
          RiskLevel.ORANGE -> "current location is near an active hazard area"
          RiskLevel.YELLOW -> "a hazard watch covers the area"
          RiskLevel.GREEN -> "no active hazard covers the area"
        }
      )
      if (vulnerableCategoryIds.isNotEmpty()) {
        val labels = vulnerableCategoryIds.mapNotNull { id ->
          VULNERABLE_CATEGORIES.firstOrNull { it.id == id }?.label
        }
        if (labels.isNotEmpty()) append(" with vulnerable members: ${labels.joinToString(", ")}")
      }
      append(".")
    }

    return RelocationPlan(
      priorityBand = band,
      priorityScore = score,
      bandExplanation = bandExplanation,
      assignedShelter = assignment,
      vulnerableCategories = VULNERABLE_CATEGORIES.filter { it.id in vulnerableCategoryIds },
      overflowNote = overflow?.redistributionNote
    )
  }
}
