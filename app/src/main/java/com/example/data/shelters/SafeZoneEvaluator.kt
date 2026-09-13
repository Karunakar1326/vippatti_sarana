package com.example.data.shelters

import com.example.data.hazards.HazardAnalysisService
import com.example.data.model.GeoMath
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint

/**
 * Why a candidate shelter was REJECTED (hard ineligibility).
 */
enum class RejectionReason(val label: String) {
  INSIDE_HAZARD_AREA("Inside an active hazard area"),
  NO_REMAINING_CAPACITY("Full — no remaining capacity"),
  UNREACHABLE("Unreachable"),
  NOT_OPERATING("Not operating")
}

/** User-facing ranking reason shown in the "Why this safe zone?" panel. */
data class SelectionReason(val text: String)

/**
 * A fully evaluated safe-zone candidate.
 */
data class SafeZoneEvaluation(
  val zone: SafeZone,
  val distanceMeters: Double,
  val isFeasible: Boolean,
  val rejectionReason: RejectionReason?,
  /** Higher is better. */
  val score: Int,
  val reasons: List<SelectionReason>,
  val hazardExposureCount: Int,
  val capacityReport: ShelterCapacityService.CapacityReport
) {
  val rankExplanation: String get() = reasons.joinToString(" • ") { it.text }
}

/**
 * Structured safe-zone selection engine.
 *
 * Replaces "nearest shelter only" thinking with a two-phase decision:
 *
 *   PHASE 1 — REJECT ineligibles: inside hazard area / full / not operating /
 *   unreachable (unreachable is reserved for future blocked-road data).
 *
 *   PHASE 2 — RANK feasible survivors on safety, remaining capacity, distance,
 *   travel time, accessibility, resources, medical support and vulnerable
 *   suitability.
 */
object SafeZoneEvaluator {

  /** Candidate-origin context for one decision run. */
  data class RequestContext(
    val origin: GeoPoint,
    val hazards: List<com.example.data.model.HazardZone>,
    val walkingSpeedMps: Double = 1.35,
    val hasVulnerableMembers: Boolean = false,
    val needsMedicalSupport: Boolean = false
  )

  fun evaluateAll(zones: List<SafeZone>, ctx: RequestContext): List<SafeZoneEvaluation> =
    zones.map { evaluate(it, ctx) }

  fun evaluate(zone: SafeZone, ctx: RequestContext): SafeZoneEvaluation {
    val distance = GeoMath.distanceMeters(ctx.origin, zone.point)
    val exposures = HazardAnalysisService.affectingHazards(zone.point, ctx.hazards)
    val capacity = ShelterCapacityService.report(zone)
    val reasons = mutableListOf<SelectionReason>()

    // -------- PHASE 1: hard rejections --------------------------------------
    var rejection: RejectionReason? = null
    if (exposures.isNotEmpty()) {
      rejection = RejectionReason.INSIDE_HAZARD_AREA
    } else if (!capacity.acceptsNewOccupants) {
      rejection = RejectionReason.NO_REMAINING_CAPACITY
    } else if (zone.operatingStatus.trim().uppercase() != "OPEN") {
      rejection = RejectionReason.NOT_OPERATING
    }
    // Unreachable: reserved for future blocked-road/bridge data — no
    // fabricated road closures at this stage.
    if (rejection == null && distance > UNREACHABLE_DISTANCE_LIMIT_METERS) {
      rejection = RejectionReason.UNREACHABLE
    }

    if (rejection != null) {
      return SafeZoneEvaluation(
        zone = zone,
        distanceMeters = distance,
        isFeasible = false,
        rejectionReason = rejection,
        score = 0,
        reasons = listOf(SelectionReason(rejection.label)),
        hazardExposureCount = exposures.size,
        capacityReport = capacity
      )
    }

    // -------- PHASE 2: composite ranking score ------------------------------
    val safetyScore = (100 - exposures.size * 25).coerceAtLeast(0)
    val capacityScore = if (zone.capacityTotal > 0) {
      ((capacity.availableCapacity.toFloat() / zone.capacityTotal) * 100f).toInt().coerceIn(0, 100)
    } else 0
    val distanceScore = (100 - (distance / UNREACHABLE_DISTANCE_LIMIT_METERS) * 100)
      .coerceIn(0.0, 100.0).toInt()
    val resourceScore = listOf(
      zone.waterAvailable,
      zone.foodAvailable,
      zone.electricityAvailable,
      zone.sanitationAvailable
    ).count { it } * 25
    val medicalScore = if (zone.medicalSupport) 100 else 0
    val accessibilityScore = when {
      zone.accessibility.contains("wheelchair", ignoreCase = true) ||
        zone.accessibility.contains("ambulance", ignoreCase = true) -> 100
      zone.accessibility.contains("Highway", ignoreCase = true) ||
        zone.accessibility.contains("District HQ", ignoreCase = true) -> 80
      else -> 40
    }
    val vulnerableScore = when {
      zone.womenChildrenSuitability && zone.medicalSupport -> 100
      zone.womenChildrenSuitability -> 65
      else -> 30
    }
    var score = (
      safetyScore * W_SAFETY +
        capacityScore * W_CAPACITY +
        distanceScore * W_DISTANCE +
        accessibilityScore * W_ACCESS +
        resourceScore * W_RESOURCES +
        medicalScore * W_MEDICAL +
        vulnerableScore * W_VULNERABLE
      ).toInt()
    if (ctx.needsMedicalSupport && zone.medicalSupport) score += BONUS_MEDICAL_NEEDED
    if (ctx.hasVulnerableMembers && zone.womenChildrenSuitability) score += BONUS_VULNERABLE
    score = score.coerceIn(0, 100 + BONUS_MEDICAL_NEEDED + BONUS_VULNERABLE)

    // -------- User-facing "Why this safe zone?" reasons ----------------------
    if (exposures.isEmpty()) {
      reasons += SelectionReason("Lower hazard exposure — outside all active hazard areas")
    }
    if (capacity.availableCapacity > 0) {
      reasons += SelectionReason(
        "${capacity.availableCapacity} people capacity remaining (${capacity.statusLabel})"
      )
    }
    reasons += SelectionReason(
      "About ${GeoMath.formatKm(distance)} away (~${estimateTravelMinutes(distance, ctx.walkingSpeedMps)} min walk)"
    )
    if (zone.medicalSupport) reasons += SelectionReason("Medical support available on site")
    if (ctx.hasVulnerableMembers && zone.womenChildrenSuitability) {
      reasons += SelectionReason("Suitable for women, children and elderly evacuees")
    }
    if (zone.waterAvailable && zone.foodAvailable) {
      reasons += SelectionReason("Water and food reserves stocked")
    }
    if (zone.accessibility.contains("wheelchair", ignoreCase = true)) {
      reasons += SelectionReason("Wheelchair accessible")
    }

    return SafeZoneEvaluation(
      zone = zone,
      distanceMeters = distance,
      isFeasible = true,
      rejectionReason = null,
      score = score,
      reasons = reasons,
      hazardExposureCount = exposures.size,
      capacityReport = capacity
    )
  }

  /** Ranked candidate list (feasible only, best first). */
  fun ranked(zones: List<SafeZone>, ctx: RequestContext): List<SafeZoneEvaluation> =
    evaluateAll(zones, ctx).filter { it.isFeasible }.sortedByDescending { it.score }

  fun estimateTravelMinutes(distanceMeters: Double, speedMps: Double): Int =
    (distanceMeters / speedMps / 60.0).toInt().coerceAtLeast(1)

  // Composite weights: safety dominates, then capacity, distance, access,
  // resources, medical, vulnerable suitability.
  private const val W_SAFETY = 0.30
  private const val W_CAPACITY = 0.20
  private const val W_DISTANCE = 0.15
  private const val W_ACCESS = 0.10
  private const val W_RESOURCES = 0.10
  private const val W_MEDICAL = 0.10
  private const val W_VULNERABLE = 0.05
  private const val BONUS_MEDICAL_NEEDED = 10
  private const val BONUS_VULNERABLE = 10
  private const val UNREACHABLE_DISTANCE_LIMIT_METERS = 40_000.0
}
