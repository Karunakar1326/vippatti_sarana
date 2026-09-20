package com.example.data.risk

import com.example.data.disaster.PilotRegionData
import com.example.data.model.CapacityStatus
import com.example.data.model.GeoMath
import com.example.data.model.HazardSeverity
import com.example.data.routing.GeoPoint
import com.example.data.risk.RiskAssessmentEngine
import com.example.data.risk.RiskLevel
import com.example.data.shelters.ShelterCapacityService
import com.example.data.shelters.SafeZoneEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for the disaster decision engines (no Android deps).
 */
class DecisionEnginesUnitTest {

  // ------------------------------------------------------------ GeoMath
  @Test
  fun `haversine distance between known Idukki points`() {
    val painavu = GeoPoint(9.84778, 76.94222)
    val cheruthoni = GeoPoint(9.77361, 77.03528)
    val d = GeoMath.distanceMeters(painavu, cheruthoni)
    // Real-world straight-line distance is roughly 12.9 km.
    assertTrue("distance $d not plausible", d in 11_000.0..15_000.0)
  }

  @Test
  fun `point inside radius`() {
    val center = GeoPoint(9.85, 76.95)
    val near = GeoPoint(9.8501, 76.9501)
    assertTrue(GeoMath.isWithinRadius(near, center, 1000.0))
  }

  // ------------------------------------------------------------ Risk
  @Test
  fun `location inside extreme flood zone yields RED risk`() {
    val risk = RiskAssessmentEngine.assess(
      location = PilotRegionData.hazardZones[0].center, // inside the extreme flood zone
      hazards = PilotRegionData.hazardZones,
      provenanceNote = "unit-test"
    )
    assertEquals(RiskLevel.RED, risk.level)
    assertNotNull(risk.primaryHazard)
    assertTrue(risk.explanation.contains("flood", ignoreCase = true))
  }

  @Test
  fun `far-away location yields GREEN risk`() {
    // Thodupuzha shelter — far from all hazard circles.
    val risk = RiskAssessmentEngine.assess(
      location = GeoPoint(9.89998, 76.71920),
      hazards = PilotRegionData.hazardZones,
      provenanceNote = "unit-test"
    )
    assertEquals(RiskLevel.GREEN, risk.level)
  }

  // -------------------------------------------------------- Capacity
  @Test
  fun `capacity statuses vary across the shelter network`() {
    val statuses = PilotRegionData.safeZones.map { it.capacityStatus }.toSet()
    // The pilot dataset deliberately covers multiple statuses.
    assertTrue(statuses.contains(CapacityStatus.AVAILABLE))
    assertTrue(statuses.contains(CapacityStatus.FULL) || statuses.contains(CapacityStatus.NEAR_CAPACITY))
  }

  @Test
  fun `full shelter report has zero availability`() {
    val full = PilotRegionData.safeZones.first { it.capacityCurrent >= it.capacityTotal }
    val report = ShelterCapacityService.report(full)
    assertEquals(CapacityStatus.FULL, report.status)
    assertEquals(0, report.availableCapacity)
  }

  @Test
  fun `projected status escalates to overflow when surge exceeds capacity`() {
    val shelter = PilotRegionData.safeZones.first { it.availableCapacity > 20 }
    val projected = ShelterCapacityService.projectedStatus(shelter, incomingPeople = shelter.availableCapacity + 50)
    assertEquals(CapacityStatus.OVERFLOW_REQUIRED, projected)
  }

  // ------------------------------------------------------ Evaluator
  @Test
  fun `evaluator rejects shelters inside hazard zones`() {
    // Synthetic shelter AT the Assam flood centre (no mock shelter sits
    // inside danger by design) pins the INSIDE_HAZARD_AREA path.
    val flood = PilotRegionData.hazardZones.first { it.id == "hz-flood-assam-dibrugarh" }
    val inside = PilotRegionData.safeZones.first { it.id == "sz-assam-dibrugarh-hall" }
      .copy(id = "sz-synthetic-inside", lat = flood.center.lat, lon = flood.center.lon)
    val ctx = SafeZoneEvaluator.RequestContext(
      origin = GeoPoint(27.53, 94.97),
      hazards = PilotRegionData.hazardZones
    )
    val evaluation = SafeZoneEvaluator.evaluate(inside, ctx)
    assertEquals(false, evaluation.isFeasible)
    assertEquals(
      com.example.data.shelters.RejectionReason.INSIDE_HAZARD_AREA,
      evaluation.rejectionReason
    )
  }

  @Test
  fun `evaluator rejects full shelters`() {
    val ctx = SafeZoneEvaluator.RequestContext(
      origin = GeoPoint(9.85, 76.95),
      hazards = PilotRegionData.hazardZones
    )
    val full = PilotRegionData.safeZones.first { it.capacityStatus == CapacityStatus.FULL }
    val evaluation = SafeZoneEvaluator.evaluate(full, ctx)
    assertEquals(false, evaluation.isFeasible)
  }

  @Test
  fun `ranked list only contains feasible shelters, best first`() {
    val ctx = SafeZoneEvaluator.RequestContext(
      origin = GeoPoint(9.85, 76.95),
      hazards = PilotRegionData.hazardZones
    )
    val ranked = SafeZoneEvaluator.ranked(PilotRegionData.safeZones, ctx)
    assertTrue(ranked.isNotEmpty())
    assertTrue(ranked.all { it.isFeasible })
    assertTrue(ranked.zipWithNext().all { (a, b) -> a.score >= b.score })
    // Every ranked shelter explains WHY.
    assertTrue(ranked.all { it.reasons.isNotEmpty() })
  }

  @Test
  fun `hazard severity weights are ordered`() {
    assertTrue(HazardSeverity.EXTREME.weight > HazardSeverity.HIGH.weight)
    assertTrue(HazardSeverity.HIGH.weight > HazardSeverity.MODERATE.weight)
    assertTrue(HazardSeverity.MODERATE.weight > HazardSeverity.LOW.weight)
  }
}
