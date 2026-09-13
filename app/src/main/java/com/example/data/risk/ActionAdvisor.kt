package com.example.data.risk

import com.example.data.model.HazardType
import com.example.data.shelters.SafeZoneEvaluation

/**
 * The single actionable recommendation the app presents: "What should I do?"
 */
data class RecommendedAction(
  val title: String,
  /** Machine identifier, e.g. EVACUATE_NOW, SHELTER_IN_PLACE. */
  val actionId: String,
  val explanation: String,
  val targetShelterName: String?
) {
  val hasEvacuationTarget: Boolean get() = targetShelterName != null
}

/**
 * "WHAT SHOULD I DO?" engine.
 *
 * Maps (personal risk level, hazard type, feasible shelter options) to one
 * clear action, ALWAYS with a why-explanation. Extensible for future
 * continuous recalculation: recompute on every GPS fix / hazard update and the
 * recommendation adapts.
 */
object ActionAdvisor {

  fun recommend(
    risk: PersonalRiskAssessment,
    rankedShelters: List<SafeZoneEvaluation>
  ): RecommendedAction {
    val primary = risk.primaryHazard
    val best = rankedShelters.firstOrNull()
    val shelterLine = best?.let { "Nearest feasible safe zone: ${it.zone.name}." } ?: ""

    // ---- RED: life-safety threat -----------------------------------------
    if (risk.level == RiskLevel.RED) {
      return when (primary?.type) {
        HazardType.FLOOD -> RecommendedAction(
          title = "MOVE TO HIGHER GROUND NOW",
          actionId = "MOVE_TO_HIGHER_GROUND",
          explanation = "Your location is inside the ${primary.name} flood area with " +
            "worsening conditions. $shelterLine",
          targetShelterName = best?.zone?.name
        )
        else -> RecommendedAction(
          title = "EVACUATE NOW",
          actionId = "EVACUATE_NOW",
          explanation = "Your location has extreme hazard exposure " +
            "(${primary?.name ?: "multiple hazards"}). Move to a feasible safe zone immediately. $shelterLine",
          targetShelterName = best?.zone?.name
        )
      }
    }

    // ---- ORANGE: prepare / be ready to move -------------------------------
    if (risk.level == RiskLevel.ORANGE) {
      return when (primary?.type) {
        HazardType.FLOOD -> RecommendedAction(
          title = "MOVE AWAY FROM RIVER",
          actionId = "MOVE_AWAY_FROM_RIVER",
          explanation = "You are near the ${primary.name}. Move away from the river and " +
            "prepare to evacuate to higher ground. $shelterLine",
          targetShelterName = best?.zone?.name
        )
        HazardType.LANDSLIDE -> RecommendedAction(
          title = "PREPARE TO EVACUATE",
          actionId = "PREPARE_TO_EVACUATE",
          explanation = "You are inside the ${primary.name}. Keep your household ready to " +
            "move to a safe zone at short notice. $shelterLine",
          targetShelterName = best?.zone?.name
        )
        else -> RecommendedAction(
          title = "PREPARE TO EVACUATE",
          actionId = "PREPARE_TO_EVACUATE",
          explanation = "Hazard exposure at your location is high (${primary?.name ?: "watch area"}). " +
            "Prepare to relocate. $shelterLine",
          targetShelterName = best?.zone?.name
        )
      }
    }

    // ---- YELLOW: vigilance --------------------------------------------------
    if (risk.level == RiskLevel.YELLOW) {
      return RecommendedAction(
        title = "WAIT FOR OFFICIAL EVACUATION ORDER",
        actionId = "WAIT_FOR_OFFICIAL_ORDER",
        explanation = "Low-level hazard watch covers your area. Monitor official " +
          "KSDMA/NDMA channels and keep your emergency kit ready. $shelterLine",
        targetShelterName = best?.zone?.name
      )
    }

    // ---- GREEN: safe ---------------------------------------------------------
    return RecommendedAction(
      title = "NO TRAVEL RESTRICTION — STAY ALERT",
      actionId = "STAY_ALERT",
      explanation = risk.explanation +
        " No evacuation needed. Avoid flooded roads and monitor updates.",
      targetShelterName = null
    )
  }
}
