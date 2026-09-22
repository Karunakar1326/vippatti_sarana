package com.example.data.capacity

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.SafeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CARRYING CAPACITY - engine contracts.
 *
 * The engine must compute effective capacity as the minimum over the
 * constraints that actually have values, name the limiting resource, and never
 * turn missing data into zero or into a verdict.
 */
class CarryingCapacityEngineTest {

  private val now = 1_800_000_000_000L

  private fun site(
    id: String = "site-1",
    total: Int = 500,
    current: Int = 100,
    land: Double? = null,
    water: Double? = null,
    toilets: Int? = null,
    waterAvailable: Boolean = true,
    sanitationAvailable: Boolean = true,
    foodAvailable: Boolean = true,
    classification: DataClassification = DataClassification.OBSERVED,
    verified: Boolean = true
  ) = SafeZone(
    id = id,
    name = "Test Relief Site $id",
    lat = 20.0,
    lon = 78.0,
    locationNote = "test",
    capacityTotal = total,
    capacityCurrent = current,
    waterAvailable = waterAvailable,
    foodAvailable = foodAvailable,
    electricityAvailable = true,
    sanitationAvailable = sanitationAvailable,
    medicalSupport = false,
    accessibility = "Road accessible",
    womenChildrenSuitability = true,
    operatingStatus = "OPEN",
    verificationStatus = "test record",
    elevationNote = "",
    landAreaSquareMeters = land,
    waterLitresPerDay = water,
    toiletCount = toilets,
    provenance = DataProvenance(
      source = "test registry",
      isVerified = verified,
      classification = classification
    )
  )

  private fun demand(people: Int?) = RelocationDemand(
    people = people,
    state = ResourceDataState.USER_DECLARED,
    source = "Citizen profile",
    basis = "test demand"
  )

  // ----------------------------------------------------- basic calculation

  @Test
  fun `effective capacity is the minimum over the available constraints`() {
    // 400 spaces, land 900/4.5 = 200, water 6000/15 = 400, toilets 6*50 = 300.
    val assessment = CarryingCapacityEngine.assess(
      site("mix", total = 500, current = 100, land = 900.0, water = 6_000.0, toilets = 6),
      demand(150),
      now
    )
    assertEquals(200, assessment.effectiveCapacity)
    assertEquals(CapacityResource.LAND_AREA, assessment.limitingResource)
    assertEquals(FeasibilityStatus.FEASIBLE, assessment.status)
    assertEquals(50, assessment.remainingCapacity)
    assertNull(assessment.shortfall)
    assertTrue(assessment.meetsRequirement == true)
  }

  @Test
  fun `space-only records still produce a verdict from real data`() {
    val assessment = CarryingCapacityEngine.assess(
      site(total = 300, current = 75),
      demand(100),
      now
    )
    assertEquals(225, assessment.effectiveCapacity)
    assertEquals(CapacityResource.SHELTER_SPACES, assessment.limitingResource)
    assertEquals(FeasibilityStatus.FEASIBLE, assessment.status)
    assertEquals(125, assessment.remainingCapacity)
  }

  // --------------------------------------------------- multiple constraints

  @Test
  fun `the limiting resource is the smallest constraint, not list order`() {
    val waterLimited = CarryingCapacityEngine.assess(
      site("water", total = 900, current = 100, land = 9_000.0, water = 3_000.0, toilets = 20),
      demand(100),
      now
    )
    assertEquals(200, waterLimited.effectiveCapacity) // 3000 L / 15 L
    assertEquals(CapacityResource.WATER, waterLimited.limitingResource)

    val sanitationLimited = CarryingCapacityEngine.assess(
      site("sani", total = 900, current = 100, land = 9_000.0, water = 30_000.0, toilets = 2),
      demand(100),
      now
    )
    assertEquals(100, sanitationLimited.effectiveCapacity) // 2 toilets x 50
    assertEquals(CapacityResource.SANITATION, sanitationLimited.limitingResource)
  }

  @Test
  fun `the tie-break order is stable when two constraints match`() {
    // Water 3000/15 = 200 and land 900/4.5 = 200: water wins by the documented order.
    val assessment = CarryingCapacityEngine.assess(
      site("tie", total = 900, current = 100, land = 900.0, water = 3_000.0, toilets = 20),
      demand(50),
      now
    )
    assertEquals(200, assessment.effectiveCapacity)
    assertEquals(CapacityResource.WATER, assessment.limitingResource)
  }

  // ------------------------------------------------- under / over capacity

  @Test
  fun `an over-capacity site reports a shortfall`() {
    val assessment = CarryingCapacityEngine.assess(
      site(total = 200, current = 150),
      demand(300),
      now
    )
    assertEquals(50, assessment.effectiveCapacity)
    assertEquals(FeasibilityStatus.INFEASIBLE, assessment.status)
    assertEquals(250, assessment.shortfall)
    assertNull(assessment.remainingCapacity)
    assertTrue(assessment.meetsRequirement == false)
    assertTrue(assessment.explanation.contains("Shortfall 250"))
  }

  @Test
  fun `exactly matching demand is feasible with zero remaining`() {
    val assessment = CarryingCapacityEngine.assess(site(total = 200, current = 100), demand(100), now)
    assertEquals(100, assessment.effectiveCapacity)
    assertEquals(FeasibilityStatus.FEASIBLE, assessment.status)
    assertEquals(0, assessment.remainingCapacity)
    assertNull(assessment.shortfall)
  }

  // ------------------------------------------- missing and invalid values

  @Test
  fun `a missing measurement is reported as not provided and never as zero`() {
    val assessment = CarryingCapacityEngine.assess(
      site(total = 500, current = 100, land = null, water = null, toilets = null),
      demand(100),
      now
    )
    val water = assessment.resources.first { it.resource == CapacityResource.WATER }
    assertNull(water.peopleSupported)
    assertEquals(ResourceDataState.NOT_PROVIDED, water.state)
    assertFalse(water.recordedAbsence)
    // The minimum comes only from the resources that have values.
    assertEquals(400, assessment.effectiveCapacity)
    assertTrue(assessment.unavailableResources.contains(CapacityResource.WATER))
    assertTrue(assessment.explanation.contains("Not assessed"))
  }

  @Test
  fun `a recorded absence is a real fact and caps the site at zero`() {
    val assessment = CarryingCapacityEngine.assess(
      site("dry", total = 500, current = 0, water = null, waterAvailable = false),
      demand(10),
      now
    )
    val water = assessment.resources.first { it.resource == CapacityResource.WATER }
    assertEquals(0, water.peopleSupported)
    assertTrue(water.recordedAbsence)
    assertEquals(0, assessment.effectiveCapacity)
    assertEquals(CapacityResource.WATER, assessment.limitingResource)
    assertEquals(FeasibilityStatus.INFEASIBLE, assessment.status)
    assertEquals(10, assessment.shortfall)
  }

  @Test
  fun `zero and negative measurements are treated as absence, not as negative people`() {
    val assessment = CarryingCapacityEngine.assess(
      site("invalid", total = 400, current = 100, land = -5.0, water = 0.0, toilets = -3),
      demand(50),
      now
    )
    assertEquals(0, assessment.resources.first { it.resource == CapacityResource.WATER }.peopleSupported)
    assertEquals(0, assessment.resources.first { it.resource == CapacityResource.LAND_AREA }.peopleSupported)
    assertEquals(0, assessment.resources.first { it.resource == CapacityResource.SANITATION }.peopleSupported)
    assertEquals(0, assessment.effectiveCapacity)
    assertEquals(FeasibilityStatus.INFEASIBLE, assessment.status)
  }

  @Test
  fun `a record with no usable capacity figure reports insufficient data`() {
    val assessment = CarryingCapacityEngine.assess(
      site("blank", total = 0, current = 0),
      demand(20),
      now
    )
    assertEquals(FeasibilityStatus.INSUFFICIENT_DATA, assessment.status)
    assertNull(assessment.effectiveCapacity)
    assertNull(assessment.meetsRequirement)
    assertNull(assessment.remainingCapacity)
    assertNull(assessment.shortfall)
    assertTrue(assessment.explanation.contains("no feasibility verdict"))
  }

  // -------------------------------------------------- INSUFFICIENT vs INFEASIBLE

  @Test
  fun `no population figure is INSUFFICIENT_DATA even when the site is empty`() {
    val assessment = CarryingCapacityEngine.assess(site(total = 5_000, current = 0), demand(null), now)
    assertEquals(FeasibilityStatus.INSUFFICIENT_DATA, assessment.status)
    assertNull(assessment.meetsRequirement)
    // The site's own capacity is still reported honestly.
    assertEquals(5_000, assessment.effectiveCapacity)
    assertTrue(assessment.explanation.contains("census"))
  }

  @Test
  fun `a zero population demand is treated as no demand, not as feasible for nobody`() {
    val assessment = CarryingCapacityEngine.assess(site(total = 100, current = 0), demand(0), now)
    assertEquals(FeasibilityStatus.INSUFFICIENT_DATA, assessment.status)
    assertNull(assessment.meetsRequirement)
  }

  // ------------------------------------ verified, estimated and simulated

  @Test
  fun `simulated site records never produce a verified verdict`() {
    val assessment = CarryingCapacityEngine.assess(
      site(classification = DataClassification.SIMULATED, verified = false),
      demand(100),
      now
    )
    assertEquals(FeasibilityStatus.SIMULATED, assessment.status)
    assertEquals(DataClassification.SIMULATED, assessment.provenance.classification)
    assertFalse(assessment.provenance.isVerified)
    // The arithmetic verdict is still available and honest.
    assertTrue(assessment.meetsRequirement == true)
    assertTrue(assessment.explanation.contains("SIMULATED"))
  }

  @Test
  fun `a mixed record is classified as estimated, and measured data as observed`() {
    val measured = CarryingCapacityEngine.assess(
      site(classification = DataClassification.OBSERVED, verified = true),
      demand(10),
      now
    )
    assertEquals(DataClassification.OBSERVED, measured.provenance.classification)
    assertTrue(measured.provenance.isVerified)
    assertEquals(FeasibilityStatus.FEASIBLE, measured.status)

    val partial = CarryingCapacityEngine.assess(
      site(classification = DataClassification.DERIVED, verified = false),
      demand(10),
      now
    )
    assertEquals(ResourceDataState.ESTIMATED, partial.resources.first().state)
    assertEquals(DataClassification.ESTIMATED, partial.provenance.classification)
    assertEquals(FeasibilityStatus.FEASIBLE, partial.status)
  }

  @Test
  fun `a simulated demand makes the verdict simulated even on measured site data`() {
    val simulatedDemand = RelocationDemand(
      people = 100,
      state = ResourceDataState.SIMULATED,
      source = "Pilot demo population record",
      basis = "demo demand"
    )
    val assessment = CarryingCapacityEngine.assess(
      site(classification = DataClassification.OBSERVED, verified = true),
      simulatedDemand,
      now
    )
    assertEquals(FeasibilityStatus.SIMULATED, assessment.status)
    // The real site arithmetic is still reported, and the reason is explicit.
    assertTrue(assessment.meetsRequirement == true)
    assertTrue(assessment.explanation.contains("Demand is SIMULATED"))
  }

  @Test
  fun `a resolved population demand drives the verdict and keeps its provenance`() {
    val resolution = com.example.data.population.PopulationDemandResolver.resolve(
      records = listOf(
        com.example.data.population.PopulationRecord(
          id = "ward-demand",
          role = com.example.data.population.PopulationRole.RELOCATION_DEMAND,
          scope = com.example.data.population.PopulationScope.WARD,
          areaName = "Ward 4",
          value = 400,
          classification = com.example.data.population.PopulationClassification.VERIFIED,
          sourceKind = com.example.data.population.PopulationSourceKind.AUTHORITY_ASSESSMENT,
          source = "authority field record",
          referenceMillis = now - 3_600_000L
        )
      )
    )
    // 400 spaces, 6 toilets x 50 = 300 people, water 9000/15 = 600 people.
    val assessment = CarryingCapacityEngine.assess(
      site(total = 500, current = 100, water = 9_000.0, toilets = 6),
      resolution.demand,
      now
    )
    assertEquals(400, assessment.demand.people)
    assertEquals(CapacityResource.SANITATION, assessment.limitingResource)
    assertEquals(FeasibilityStatus.INFEASIBLE, assessment.status)
    assertEquals(100, assessment.shortfall)
    assertEquals("Ward 4 (Ward)", assessment.demand.scopeLabel)
  }

  @Test
  fun `insufficient data still reports the constraints the record DID provide`() {
    val assessment = CarryingCapacityEngine.assess(
      site("incomplete", total = 100, current = 0, land = null, water = null, toilets = null),
      demand(null),
      now
    )
    assertEquals(FeasibilityStatus.INSUFFICIENT_DATA, assessment.status)
    assertNull(assessment.meetsRequirement)
    // Spaces are real, so they stay visible; the missing ones stay "not provided".
    assertEquals(100, assessment.effectiveCapacity)
    assertEquals(CapacityResource.SHELTER_SPACES, assessment.limitingResource)
    assertTrue(assessment.unavailableResources.contains(CapacityResource.LAND_AREA))
  }

  // ------------------------------------------------------ explanations

  @Test
  fun `every verdict carries the required explanation fields`() {
    val assessment = CarryingCapacityEngine.assess(
      site("explain", total = 500, current = 100, water = 6_000.0),
      demand(200),
      now
    )
    assertNotNull(assessment.limitingResource)
    assertTrue(assessment.explanation.isNotBlank())
    assertTrue(assessment.assumptions.isNotEmpty())
    assertEquals(now, assessment.assessedAtMillis)
    assertTrue(assessment.sourceLine.contains("test registry"))
    assertTrue(assessment.assumptions.any { it.contains("configured app figure") })
  }

  // ------------------------------------------------ relocation integration

  @Test
  fun `the planner prefers a site whose capacity actually fits the demand`() {
    val small = site("small", total = 100, current = 0)
    val big = site("big", total = 900, current = 0)
    val ranked = com.example.data.shelters.SafeZoneEvaluator.evaluateAll(
      listOf(small, big),
      com.example.data.shelters.SafeZoneEvaluator.RequestContext(
        origin = com.example.data.routing.GeoPoint(20.0, 78.0),
        hazards = emptyList()
      )
    ).sortedByDescending { it.score }

    val assessments = mapOf(
      "small" to CarryingCapacityEngine.assess(small, demand(500), now),
      "big" to CarryingCapacityEngine.assess(big, demand(500), now)
    )
    val plan = com.example.data.risk.RelocationPlanner.plan(
      risk = com.example.data.risk.RiskAssessmentEngine.assess(
        location = com.example.data.routing.GeoPoint(20.0, 78.0),
        hazards = emptyList(),
        provenanceNote = "test"
      ),
      rankedShelters = ranked,
      vulnerableCategoryIds = setOf("elderly"),
      capacityAssessments = assessments
    )
    assertEquals("big", plan.assignedShelter?.zone?.id)
    assertNotNull(plan.capacityAssessment)
    assertTrue(plan.feasibilityNote!!.contains("can absorb"))
    // Small site: 100 spaces vs 500 required -> short by 400, reported verbatim.
    assertTrue(plan.feasibilityNote!!.contains("short by 400"))
    assertTrue(plan.feasibilityNote!!.contains("Capacity-checked and skipped"))
    // The skipped site is structured data too, not only prose.
    assertEquals(1, plan.skippedSites.size)
    assertEquals("small", plan.skippedSites.first().siteId)
    assertEquals(400, plan.skippedSites.first().shortfall)
    assertEquals(FeasibilityStatus.INFEASIBLE, plan.skippedSites.first().status?.status)
    assertTrue(plan.skippedSites.first().reason.contains("short by 400 people"))
  }

  @Test
  fun `an unproven site is not reported as skipped`() {
    val unproven = site("unproven", total = 0, current = 0, land = null, water = null, toilets = null)
    val ranked = com.example.data.shelters.SafeZoneEvaluator.evaluateAll(
      listOf(unproven),
      com.example.data.shelters.SafeZoneEvaluator.RequestContext(
        origin = com.example.data.routing.GeoPoint(20.0, 78.0),
        hazards = emptyList()
      )
    )
    val plan = com.example.data.risk.RelocationPlanner.plan(
      risk = com.example.data.risk.RiskAssessmentEngine.assess(
        location = com.example.data.routing.GeoPoint(20.0, 78.0),
        hazards = emptyList(),
        provenanceNote = "test"
      ),
      rankedShelters = ranked,
      vulnerableCategoryIds = emptySet(),
      capacityAssessments = mapOf(
        "unproven" to CarryingCapacityEngine.assess(unproven, demand(500), now)
      )
    )
    // INSUFFICIENT_DATA is unproven, not infeasible: it may still be assigned.
    assertEquals("unproven", plan.assignedShelter?.zone?.id)
    assertEquals(FeasibilityStatus.INSUFFICIENT_DATA, plan.capacityAssessment?.status)
    assertTrue(plan.skippedSites.isEmpty())
  }

  @Test
  fun `without assessments the planner behaves exactly as before`() {
    val only = site("only", total = 100, current = 0)
    val ranked = com.example.data.shelters.SafeZoneEvaluator.evaluateAll(
      listOf(only),
      com.example.data.shelters.SafeZoneEvaluator.RequestContext(
        origin = com.example.data.routing.GeoPoint(20.0, 78.0),
        hazards = emptyList()
      )
    ).sortedByDescending { it.score }
    val plan = com.example.data.risk.RelocationPlanner.plan(
      risk = com.example.data.risk.RiskAssessmentEngine.assess(
        location = com.example.data.routing.GeoPoint(20.0, 78.0),
        hazards = emptyList(),
        provenanceNote = "test"
      ),
      rankedShelters = ranked,
      vulnerableCategoryIds = emptySet()
    )
    assertEquals("only", plan.assignedShelter?.zone?.id)
    assertNull(plan.capacityAssessment)
    // With no assessments the assignment is untouched, and the note says plainly
    // that capacity was not checked instead of implying it passed.
    assertEquals("Carrying capacity not assessed for Test Relief Site only.", plan.feasibilityNote)
  }
}
