package com.example.data.shelters

import com.example.data.model.CapacityStatus
import com.example.data.model.SafeZone

/**
 * Carrying-capacity intelligence.
 *
 * Works on the current stored occupancy data today and is structured for
 * future dynamic capacity updates: occupancy flows in as SafeZone records
 * (from a shelter registry feed), and every derived calculation below adapts
 * automatically. No live feed is claimed at this stage.
 */
object ShelterCapacityService {

  /**
   * Capacity line items shown in the shelter detail sheet, e.g.
   * "Total Capacity: 500 / Current Occupancy: 320 / Available Capacity: 180".
   */
  data class CapacityReport(
    val zone: SafeZone,
    val totalCapacity: Int,
    val currentOccupancy: Int,
    val availableCapacity: Int,
    val occupancyPercent: Int,
    val status: CapacityStatus
  ) {
    val statusLabel: String get() = status.label
    val acceptsNewOccupants: Boolean get() = status.acceptsNewOccupants
  }

  fun report(zone: SafeZone): CapacityReport = CapacityReport(
    zone = zone,
    totalCapacity = zone.capacityTotal,
    currentOccupancy = zone.capacityCurrent,
    availableCapacity = zone.availableCapacity,
    occupancyPercent = if (zone.capacityTotal > 0) {
      ((zone.capacityCurrent * 100f) / zone.capacityTotal).toInt().coerceIn(0, 100)
    } else 100,
    status = zone.capacityStatus
  )

  fun reportAll(zones: List<SafeZone>): List<CapacityReport> = zones.map { report(it) }

  /**
   * Network-wide capacity summary for relocation planning:
   * how many people the shelter network can still absorb right now.
   */
  data class NetworkCapacitySummary(
    val totalCapacity: Int,
    val totalOccupancy: Int,
    val totalAvailable: Int,
    val openShelterCount: Int,
    val fullShelterCount: Int
  )

  fun networkSummary(zones: List<SafeZone>): NetworkCapacitySummary = NetworkCapacitySummary(
    totalCapacity = zones.sumOf { it.capacityTotal },
    totalOccupancy = zones.sumOf { it.capacityCurrent },
    totalAvailable = zones.sumOf { it.availableCapacity },
    openShelterCount = zones.count { it.capacityStatus != CapacityStatus.FULL },
    fullShelterCount = zones.count { it.capacityStatus == CapacityStatus.FULL }
  )

  /**
   * Overflow recommendation: when the primary shelter is full, the nearest
   * shelter with meaningful remaining capacity is suggested as the overflow
   * destination, and a redistribution note is produced. Returns null when the
   * whole network is saturated.
   */
  fun overflowRecommendation(
    primary: SafeZone,
    zones: List<SafeZone>,
    distanceTo: (SafeZone) -> Double
  ): OverflowRecommendation? {
    if (primary.capacityStatus.acceptsNewOccupants) return null
    val alternative = zones
      .filter { it.id != primary.id && it.capacityStatus.acceptsNewOccupants }
      .filter { it.availableCapacity >= OVERFLOW_MIN_SPOTS }
      .minByOrNull { distanceTo(it) }
      ?: return null
    return OverflowRecommendation(
      fullShelter = primary,
      overflowShelter = alternative,
      redistributionNote = "${primary.name} is at capacity. Overflow evacuees are being " +
        "reassigned to ${alternative.name} (${alternative.availableCapacity} spots open)."
    )
  }

  data class OverflowRecommendation(
    val fullShelter: SafeZone,
    val overflowShelter: SafeZone,
    val redistributionNote: String
  )

  /**
   * Shelter load balancing: rank shelters by available capacity headroom so a
   * surge of evacuees spreads across the network instead of overloading one
   * shelter. Prepared for population redistribution planning; uses current
   * stored occupancy only.
   */
  fun loadBalancedOrder(zones: List<SafeZone>): List<SafeZone> =
    zones.sortedByDescending { it.availableCapacity }

  /**
   * Predicted capacity pressure once [incomingPeople] more evacuees arrive at
   * [zone]. Returns the projected status — used by overflow planning. This is
   * an ESTIMATED projection, not a live occupancy reading.
   */
  fun projectedStatus(zone: SafeZone, incomingPeople: Int): CapacityStatus {
    val projectedOccupancy = zone.capacityCurrent + incomingPeople
    val projectedAvailable = zone.capacityTotal - projectedOccupancy
    return when {
      projectedAvailable < 0 -> CapacityStatus.OVERFLOW_REQUIRED
      projectedAvailable < SafeZone.CAPACITY_NEAR_THRESHOLD * zone.capacityTotal -> CapacityStatus.NEAR_CAPACITY
      else -> CapacityStatus.AVAILABLE
    }
  }

  private const val OVERFLOW_MIN_SPOTS = 10
}
