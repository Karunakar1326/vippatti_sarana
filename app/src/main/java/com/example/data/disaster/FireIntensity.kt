package com.example.data.disaster

import com.example.data.model.HazardSeverity

/**
 * ============================================================================
 * FIRE INTENSITY - derived from the PROVIDER's own measurement
 * ============================================================================
 *
 * NASA FIRMS returns Fire Radiative Power (FRP, megawatts) per detection. The
 * app used to label every detection "MODERATE" regardless of that value, which
 * threw away real information the provider gave us.
 *
 * [MODERATE_MW] / [HIGH_MW] / [EXTREME_MW] are CONFIGURED thresholds chosen for
 * readability on a national map; they are not an official NASA or IMD scale and
 * are documented here so the mapping is auditable and adjustable in one place.
 * A detection without FRP is never guessed at: it stays [UNKNOWN].
 */
enum class FireIntensity(val label: String) {
  LOW("low"),
  MODERATE("moderate"),
  HIGH("high"),
  EXTREME("extreme"),
  /** Provider supplied no FRP for this detection. */
  UNKNOWN("not provided")
}

object FireIntensityScale {

  /** Below this FRP the detection is a small/low-intensity fire. */
  const val MODERATE_MW = 5.0
  /** At or above this FRP the detection is a high-intensity fire. */
  const val HIGH_MW = 20.0
  /** At or above this FRP the detection is extreme. */
  const val EXTREME_MW = 100.0

  /** Largest marker growth factor applied on the map (base radius * this). */
  const val MAX_MARKER_SCALE = 2.2

  fun of(frpMegawatts: Double?): FireIntensity = when {
    frpMegawatts == null || frpMegawatts.isNaN() || frpMegawatts < 0.0 -> FireIntensity.UNKNOWN
    frpMegawatts >= EXTREME_MW -> FireIntensity.EXTREME
    frpMegawatts >= HIGH_MW -> FireIntensity.HIGH
    frpMegawatts >= MODERATE_MW -> FireIntensity.MODERATE
    else -> FireIntensity.LOW
  }

  /**
   * Hazard severity for a detection, from its real FRP and the provider's
   * confidence flag. Unknown FRP keeps the conservative middle rating (a
   * detection without a measurement is neither escalated nor dismissed), and a
   * HIGH-confidence detection with a measurement is escalated one step.
   */
  fun severityFor(frpMegawatts: Double?, confidence: EventConfidence): HazardSeverity {
    val base = when (of(frpMegawatts)) {
      FireIntensity.LOW -> HazardSeverity.LOW
      FireIntensity.MODERATE -> HazardSeverity.MODERATE
      FireIntensity.HIGH -> HazardSeverity.HIGH
      FireIntensity.EXTREME -> HazardSeverity.EXTREME
      FireIntensity.UNKNOWN -> HazardSeverity.MODERATE
    }
    if (of(frpMegawatts) == FireIntensity.UNKNOWN || confidence != EventConfidence.HIGH) return base
    return when (base) {
      HazardSeverity.LOW -> HazardSeverity.MODERATE
      HazardSeverity.MODERATE -> HazardSeverity.HIGH
      HazardSeverity.HIGH -> HazardSeverity.EXTREME
      HazardSeverity.EXTREME -> HazardSeverity.EXTREME
    }
  }

  /** FRP of one fire detection, or null when the detail block is not a fire. */
  fun frpOf(event: DisasterEvent): Double? =
    (event.details as? EventDetails.Fire)?.frpMegawatts

  /** Intensity of one fire detection (UNKNOWN for non-fire events). */
  fun of(event: DisasterEvent): FireIntensity = when (event.disasterType) {
    DisasterType.WILDFIRE -> of(frpOf(event))
    else -> FireIntensity.UNKNOWN
  }

  /**
   * Marker size factor for one drawn marker: grows with the detection's own
   * intensity and, for a cluster, with how many detections it represents. Both
   * terms are bounded by [MAX_MARKER_SCALE], and a single low-intensity fire
   * stays at 1.0 (the base radius).
   */
  fun markerScale(frpMegawatts: Double?, memberCount: Int): Double {
    val intensityGrowth = when (of(frpMegawatts)) {
      FireIntensity.LOW -> 0.0
      FireIntensity.MODERATE -> 0.2
      FireIntensity.HIGH -> 0.5
      FireIntensity.EXTREME -> 0.9
      FireIntensity.UNKNOWN -> 0.0
    }
    val clusterGrowth = if (memberCount <= 1) {
      0.0
    } else {
      (kotlin.math.ln(memberCount.toDouble()) / kotlin.math.ln(10.0)) * 0.35
    }
    return (1.0 + intensityGrowth + clusterGrowth).coerceIn(1.0, MAX_MARKER_SCALE)
  }
}
