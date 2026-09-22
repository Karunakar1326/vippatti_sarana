package com.example.data.capacity

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.SafeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WIRE FORMAT - the contract a future relief-registry / authority backend can
 * use. A round-trip must preserve every number, state and null: "not measured"
 * must never decode as 0.
 */
class CapacityAssessmentJsonTest {

  private val now = 1_800_000_000_000L

  private fun assessment(
    classification: DataClassification = DataClassification.SIMULATED
  ): CapacityAssessment {
    val site = SafeZone(
      id = "site-json",
      name = "JSON Relief Site",
      lat = 20.0,
      lon = 78.0,
      locationNote = "test",
      capacityTotal = 500,
      capacityCurrent = 100,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = false,
      medicalSupport = false,
      accessibility = "Road accessible",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "test record",
      elevationNote = "",
      landAreaSquareMeters = null, // deliberately not provided
      waterLitresPerDay = 6_000.0,
      toiletCount = null, // deliberately not provided
      provenance = DataProvenance(
        source = "test registry",
        isVerified = classification == DataClassification.OBSERVED,
        classification = classification
      )
    )
    return CarryingCapacityEngine.assess(
      site = site,
      demand = RelocationDemand.fromUserProfile(householdSize = 4, source = "Citizen profile"),
      nowMillis = now
    )
  }

  @Test
  fun `a round-trip preserves every value, state and null`() {
    val original = assessment()
    val restored = CapacityAssessmentJson.decode(CapacityAssessmentJson.encode(original).toString())

    assertTrue("payload must decode", restored != null)
    restored!!
    assertEquals(original.siteId, restored.siteId)
    assertEquals(original.siteName, restored.siteName)
    assertEquals(original.status, restored.status)
    assertEquals(original.effectiveCapacity, restored.effectiveCapacity)
    assertEquals(original.remainingCapacity, restored.remainingCapacity)
    assertEquals(original.shortfall, restored.shortfall)
    assertEquals(original.limitingResource, restored.limitingResource)
    assertEquals(original.explanation, restored.explanation)
    assertEquals(original.assumptions, restored.assumptions)
    assertEquals(original.assessedAtMillis, restored.assessedAtMillis)
    assertEquals(original.demand, restored.demand)
    // The recorded absence (sanitation) and the missing inputs (land, toilets)
    // must survive exactly as they were reported.
    assertEquals(original.resources, restored.resources)
    assertEquals(
      ResourceDataState.NOT_PROVIDED,
      restored.resources.first { it.resource == CapacityResource.LAND_AREA }.state
    )
    assertNull(restored.resources.first { it.resource == CapacityResource.LAND_AREA }.peopleSupported)
    assertTrue(restored.resources.first { it.resource == CapacityResource.SANITATION }.recordedAbsence)
  }

  @Test
  fun `provenance classification survives, so a simulated verdict stays simulated`() {
    val original = assessment(classification = DataClassification.SIMULATED)
    val restored = CapacityAssessmentJson.decode(CapacityAssessmentJson.encode(original).toString())!!
    assertEquals(FeasibilityStatus.SIMULATED, restored.status)
    assertEquals(DataClassification.SIMULATED, restored.provenance.classification)
    assertEquals(original.provenance.isVerified, restored.provenance.isVerified)
  }

  @Test
  fun `an observed assessment keeps its verified flag through the round-trip`() {
    val original = assessment(classification = DataClassification.OBSERVED)
    val restored = CapacityAssessmentJson.decode(CapacityAssessmentJson.encode(original).toString())!!
    assertEquals(DataClassification.OBSERVED, restored.provenance.classification)
    assertTrue(restored.provenance.isVerified)
    // This record marks sanitation unavailable, which honestly caps the site at
    // zero regardless of its 400 free spaces - and that reasoning survives too.
    assertEquals(FeasibilityStatus.INFEASIBLE, restored.status)
    assertEquals(CapacityResource.SANITATION, restored.limitingResource)
    assertEquals(original.meetsRequirement, restored.meetsRequirement)
  }

  @Test
  fun `an unavailable demand encodes as null, never as zero`() {
    val site = assessment().let { it.copy(demand = RelocationDemand.unavailable("no dataset")) }
    val encoded = CapacityAssessmentJson.encode(site)
    assertTrue(encoded.getJSONObject("demand").isNull("people"))
    val restored = CapacityAssessmentJson.decode(encoded.toString())!!
    assertNull(restored.demand.people)
    assertEquals(ResourceDataState.NOT_PROVIDED, restored.demand.state)
  }

  @Test
  fun `the population provenance of the demand survives the round-trip`() {
    val resolution = com.example.data.population.PopulationDemandResolver.resolve(
      records = listOf(
        com.example.data.population.PopulationRecord(
          id = "ward-demand",
          role = com.example.data.population.PopulationRole.RELOCATION_DEMAND,
          scope = com.example.data.population.PopulationScope.WARD,
          areaName = "Ward 4",
          value = 380,
          classification = com.example.data.population.PopulationClassification.ESTIMATED,
          sourceKind = com.example.data.population.PopulationSourceKind.AUTHORITY_ASSESSMENT,
          source = "authority field record",
          referenceMillis = 1_790_000_000_000L,
          confidence = 0.72
        )
      )
    )
    val site = assessment().let { it.copy(demand = resolution.demand) }
    val restored = CapacityAssessmentJson.decode(CapacityAssessmentJson.encode(site).toString())!!
    assertEquals(resolution.demand, restored.demand)
    assertEquals(com.example.data.population.PopulationScope.WARD, restored.demand.scope)
    assertEquals("Ward 4", restored.demand.scopeName)
    assertEquals(
      com.example.data.population.PopulationClassification.ESTIMATED,
      restored.demand.classification
    )
    assertEquals(1_790_000_000_000L, restored.demand.referenceMillis)
    assertEquals(0.72, restored.demand.confidence!!, 0.0001)
  }

  @Test
  fun `absent population provenance decodes as null, not as a default scope or role`() {
    val payload = """
      {"siteId":"x","status":"INSUFFICIENT_DATA","demand":{"people":null,"state":"NOT_PROVIDED",
      "source":"none","basis":"none","scope":null,"scopeName":null,"classification":null,
      "role":null,"derivedFromRole":null,"referenceMillis":null,"confidence":null},
      "resources":[],"assumptions":[],"assessedAtMillis":0,"provenance":{"source":"x"}}
    """.trimIndent()
    val restored = CapacityAssessmentJson.decode(payload)!!
    assertNull(restored.demand.scope)
    assertNull(restored.demand.classification)
    assertNull(restored.demand.role)
    assertNull(restored.demand.referenceMillis)
    assertNull(restored.demand.confidence)
    // Without provenance the demand is described generically, never as verified.
    assertEquals("Area not stated", restored.demand.scopeLabel)
  }

  @Test
  fun `malformed or incomplete payloads decode to null instead of guessing`() {
    assertNull(CapacityAssessmentJson.decode("not json"))
    assertNull(CapacityAssessmentJson.decode("{}"))
    assertNull(CapacityAssessmentJson.decode("""{"siteId":"x"}"""))
    assertNull(
      CapacityAssessmentJson.decode("""{"siteId":"x","status":"NOT_A_STATUS","demand":{}}""")
    )
  }
}
