package com.example.data.risk

import com.example.data.capacity.CapacityAssessment
import com.example.data.capacity.FeasibilityStatus
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
  val overflowNote: String?,
  /**
   * Carrying-capacity verdict for the ASSIGNED shelter, when one was supplied.
   * Null means the caller did not assess capacity - never "feasible".
   */
  val capacityAssessment: CapacityAssessment? = null,
  /** How the capacity verdict influenced (or failed to influence) assignment. */
  val feasibilityNote: String? = null,
  /** Sites that were capacity-checked and skipped, with why. Never fabricated. */
  val skippedSites: List<SkippedSite> = emptyList()
)

/**
 * A ranked site that was not assigned because its capacity verdict says it
 * cannot absorb the demand. [shortfall] is null when the site was skipped
 * without a numeric verdict (never rendered as 0).
 */
data class SkippedSite(
  val siteId: String,
  val siteName: String,
  val status: CapacityAssessment?,
  val shortfall: Int?
) {
  val reason: String
    get() = when {
      status == null -> "not assessed"
      shortfall != null -> "short by $shortfall people"
      else -> status.status.label.lowercase()
    }
}

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
    vulnerableCategoryIds: Set<String>,
    /**
     * Carrying-capacity verdicts by site id. Empty = not assessed, in which
     * case assignment is unchanged (the previous behaviour).
     */
    capacityAssessments: Map<String, CapacityAssessment> = emptyMap()
  ): RelocationPlan {
    val (band, score) = householdPriority(risk, vulnerableCategoryIds)
    // Assignment honours carrying capacity: the best-ranked site that can
    // actually absorb the declared demand wins. A site whose verdict is
    // INSUFFICIENT_DATA is not treated as infeasible - it is simply unproven -,
    // so it stays eligible after every site with a passing verdict.
    val assignment = rankedShelters.firstOrNull { candidate ->
      capacityAssessments[candidate.zone.id]?.meetsRequirement != false
    } ?: rankedShelters.firstOrNull()
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

    val assessment = assignment?.let { capacityAssessments[it.zone.id] }

    return RelocationPlan(
      skippedSites = skippedSites(assignment, rankedShelters, capacityAssessments),
      priorityBand = band,
      priorityScore = score,
      bandExplanation = bandExplanation,
      assignedShelter = assignment,
      vulnerableCategories = VULNERABLE_CATEGORIES.filter { it.id in vulnerableCategoryIds },
      overflowNote = overflow?.redistributionNote,
      capacityAssessment = assessment,
      feasibilityNote = feasibilityNote(
        assignment = assignment,
        rankedShelters = rankedShelters,
        assessment = assessment,
        capacityAssessments = capacityAssessments
      )
    )
  }

  /**
   * Every ranked site that the capacity check says cannot absorb the demand, in
   * ranking order. INSUFFICIENT_DATA is NOT a skip: an unproven site may still
   * be assigned, so it is never reported as rejected.
   */
  private fun skippedSites(
    assignment: SafeZoneEvaluation?,
    rankedShelters: List<SafeZoneEvaluation>,
    capacityAssessments: Map<String, CapacityAssessment>
  ): List<SkippedSite> = rankedShelters
    .filter { it.zone.id != assignment?.zone?.id }
    .mapNotNull { candidate ->
      val other = capacityAssessments[candidate.zone.id] ?: return@mapNotNull null
      if (other.meetsRequirement != false) return@mapNotNull null
      SkippedSite(
        siteId = candidate.zone.id,
        siteName = candidate.zone.name,
        status = other,
        shortfall = other.shortfall
      )
    }

  /**
   * Honest statement of how carrying capacity acted on the assignment: which
   * site was chosen because it fits, which site was skipped, or that nothing
   * fits and the nearest ranked site is kept with its shortfall.
   */
  private fun feasibilityNote(
    assignment: SafeZoneEvaluation?,
    rankedShelters: List<SafeZoneEvaluation>,
    assessment: CapacityAssessment?,
    capacityAssessments: Map<String, CapacityAssessment>
  ): String? {
    if (assignment == null) return null
    if (assessment == null) {
      return "Carrying capacity not assessed for ${assignment.zone.name}."
    }
    val skipped = skippedSites(assignment, rankedShelters, capacityAssessments)
      .map { site -> "${site.siteName} (${site.reason})" }
    return buildString {
      when (assessment.status) {
        FeasibilityStatus.FEASIBLE -> append(
          "${assignment.zone.name} can absorb the declared demand " +
            "(capacity ${assessment.effectiveCapacity}, remaining ${assessment.remainingCapacity})."
        )
        FeasibilityStatus.SIMULATED -> append(
          "${assignment.zone.name} fits on SIMULATED site records only — " +
            "capacity ${assessment.effectiveCapacity}, remaining ${assessment.remainingCapacity}; not verified."
        )
        FeasibilityStatus.INFEASIBLE -> append(
          "No assessed site fits the declared demand; nearest ranked site " +
            "${assignment.zone.name} kept, short by ${assessment.shortfall ?: 0} people " +
            "(${assessment.limitingResource?.label ?: "unknown constraint"})."
        )
        FeasibilityStatus.INSUFFICIENT_DATA -> append(
          "Carrying capacity for ${assignment.zone.name} is INSUFFICIENT_DATA — " +
            "no verdict issued (${assessment.explanation})."
        )
      }
      if (skipped.isNotEmpty()) {
        append(" Capacity-checked and skipped: ${skipped.joinToString(", ")}.")
      }
    }
  }


}
