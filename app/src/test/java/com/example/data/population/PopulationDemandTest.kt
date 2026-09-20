package com.example.data.population

import com.example.data.capacity.ResourceDataState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * POPULATION DEMAND - resolution contracts (SIH 26191).
 *
 * The resolver must pick the *right* population figure: a verified relocation
 * demand beats an estimate, an estimate beats affected population, affected
 * population and baseline population need explicit approval, and the citizen's
 * household size is only ever a personal input. Nothing unusable may become 0.
 */
class PopulationDemandTest {

  private val reference = 1_790_000_000_000L

  private fun record(
    id: String,
    role: PopulationRole,
    scope: PopulationScope,
    value: Int?,
    classification: PopulationClassification = PopulationClassification.VERIFIED,
    sourceKind: PopulationSourceKind = PopulationSourceKind.AUTHORITY_ASSESSMENT,
    areaName: String? = "Test Area",
    referenceMillis: Long? = reference,
    confidence: Double? = 0.8
  ) = PopulationRecord(
    id = id,
    role = role,
    scope = scope,
    areaName = areaName,
    value = value,
    classification = classification,
    sourceKind = sourceKind,
    source = "test source $id",
    referenceMillis = referenceMillis,
    confidence = confidence
  )

  private val household = PopulationRecord.householdDeclaration(
    householdSize = 5,
    source = "Citizen profile (editable, on device)"
  )

  // ------------------------------------------- classification & scope labels

  @Test
  fun `a household declaration is scoped to one household and labelled as such`() {
    assertTrue(household.isHouseholdLevel)
    assertEquals(PopulationScope.HOUSEHOLD, household.scope)
    assertEquals(PopulationClassification.USER_DECLARED, household.classification)
    assertEquals(5, household.value)
    assertTrue(
      household.notes.any { it.contains("NOT a census figure", ignoreCase = true) }
    )
  }

  @Test
  fun `a zero or negative household size supplies no value at all`() {
    val empty = PopulationRecord.householdDeclaration(0, "Citizen profile")
    assertFalse(empty.hasValue)
    assertNull(empty.value)
  }

  @Test
  fun `records keep role, scope and classification distinct`() {
    val baseline = record(
      id = "baseline",
      role = PopulationRole.BASELINE_TOTAL,
      scope = PopulationScope.VILLAGE,
      value = 12_000,
      classification = PopulationClassification.VERIFIED,
      sourceKind = PopulationSourceKind.OFFICIAL_CENSUS
    )
    assertEquals(PopulationRole.BASELINE_TOTAL, baseline.role)
    assertEquals(PopulationScope.VILLAGE, baseline.scope)
    assertEquals("Test Area (Village)", baseline.scopeLabel)
    assertTrue(baseline.attribution.contains("Verified"))
  }

  // ------------------------------------------------------ priority ordering

  @Test
  fun `a verified relocation demand wins over every other record`() {
    val resolution = PopulationDemandResolver.resolve(
      records = listOf(
        record("baseline", PopulationRole.BASELINE_TOTAL, PopulationScope.VILLAGE, 12_000,
          sourceKind = PopulationSourceKind.OFFICIAL_CENSUS),
        record("affected", PopulationRole.AFFECTED_POPULATION, PopulationScope.WARD, 900),
        record("estimate", PopulationRole.RELOCATION_DEMAND, PopulationScope.WARD, 420,
          classification = PopulationClassification.ESTIMATED),
        record("verified", PopulationRole.RELOCATION_DEMAND, PopulationScope.WARD, 380)
      ),
      householdDeclaration = household
    )
    assertEquals(380, resolution.demand.people)
    assertEquals("verified", resolution.selected?.id)
    assertNull(resolution.derivedFrom)
    assertEquals(ResourceDataState.MEASURED, resolution.demand.state)
  }

  @Test
  fun `an authority estimate is used when no verified demand exists`() {
    val resolution = PopulationDemandResolver.resolve(
      records = listOf(
        record("baseline", PopulationRole.BASELINE_TOTAL, PopulationScope.VILLAGE, 12_000,
          sourceKind = PopulationSourceKind.OFFICIAL_CENSUS),
        record("estimate", PopulationRole.RELOCATION_DEMAND, PopulationScope.WARD, 420,
          classification = PopulationClassification.ESTIMATED)
      ),
      householdDeclaration = household
    )
    assertEquals(420, resolution.demand.people)
    assertEquals(ResourceDataState.ESTIMATED, resolution.demand.state)
    assertTrue(resolution.statusLabel.startsWith("ESTIMATED"))
  }

  @Test
  fun `an unlabelled relocation demand is not trusted as an authority figure`() {
    // Same role, but the source is unknown: it falls through to the estimate
    // filters instead of being treated as an authority assessment.
    val resolution = PopulationDemandResolver.resolve(
      records = listOf(
        record("unknown-source", PopulationRole.RELOCATION_DEMAND, PopulationScope.WARD, 700,
          classification = PopulationClassification.ESTIMATED,
          sourceKind = PopulationSourceKind.UNKNOWN)
      ),
      householdDeclaration = household
    )
    assertEquals(5, resolution.demand.people)
    assertTrue(resolution.demand.isHouseholdLevel)
  }

  @Test
  fun `the narrowest area wins among equally valid records`() {
    val resolution = PopulationDemandResolver.resolve(
      records = listOf(
        record("district", PopulationRole.RELOCATION_DEMAND, PopulationScope.DISTRICT, 9_000),
        record("village", PopulationRole.RELOCATION_DEMAND, PopulationScope.VILLAGE, 600),
        record("ward", PopulationRole.RELOCATION_DEMAND, PopulationScope.WARD, 120)
      ),
      householdDeclaration = household
    )
    assertEquals("ward", resolution.selected?.id)
    assertEquals(120, resolution.demand.people)
    assertEquals("Test Area (Ward)", resolution.demand.scopeLabel)
  }

  // ------------------------------------- baseline / affected never silent

  @Test
  fun `baseline population is not converted into demand without approval`() {
    val resolution = PopulationDemandResolver.resolve(
      records = listOf(
        record("baseline", PopulationRole.BASELINE_TOTAL, PopulationScope.VILLAGE, 12_000,
          sourceKind = PopulationSourceKind.OFFICIAL_CENSUS)
      )
    )
    // The household fallback is the last resort; here nothing was supplied, so
    // the honest answer is no demand at all.
    assertNull(resolution.demand.people)
    assertEquals(ResourceDataState.NOT_PROVIDED, resolution.demand.state)
    assertEquals("INSUFFICIENT_DATA — no population source available", resolution.statusLabel)
    assertTrue(resolution.rejected.any { it.record.id == "baseline" })
    assertTrue(
      resolution.rejected.first { it.record.id == "baseline" }
        .reason.contains("Baseline population is not confirmed relocation demand")
    )
  }

  @Test
  fun `baseline population is refused even when a household size is available`() {
    val resolution = PopulationDemandResolver.resolve(
      records = listOf(
        record("baseline", PopulationRole.BASELINE_TOTAL, PopulationScope.VILLAGE, 12_000,
          sourceKind = PopulationSourceKind.OFFICIAL_CENSUS)
      ),
      householdDeclaration = household
    )
    // The personal declaration is used, NOT the baseline figure, and the demand
    // is clearly marked as household-scoped.
    assertEquals(5, resolution.demand.people)
    assertTrue(resolution.demand.isHouseholdLevel)
    assertEquals(PopulationClassification.USER_DECLARED, resolution.demand.classification)
    assertTrue(
      resolution.assumptions.any { it.contains("NOT the population of the area") }
    )
  }

  @Test
  fun `approved baseline becomes an explicitly derived estimated demand`() {
    val resolution = PopulationDemandResolver.resolve(
      records = listOf(
        record("baseline", PopulationRole.BASELINE_TOTAL, PopulationScope.VILLAGE, 12_000,
          sourceKind = PopulationSourceKind.OFFICIAL_CENSUS)
      ),
      policy = PopulationDemandPolicy(allowBaselineAsDemand = true)
    )
    assertEquals(12_000, resolution.demand.people)
    assertEquals(PopulationRole.BASELINE_TOTAL, resolution.derivedFrom)
    // A verified count used as demand is our ASSUMPTION, so it is downgraded.
    assertEquals(PopulationClassification.ESTIMATED, resolution.demand.classification)
    assertEquals(ResourceDataState.ESTIMATED, resolution.demand.state)
    assertTrue(resolution.demand.roleLabel.contains("derived from baseline population"))
    assertTrue(resolution.assumptions.any { it.contains("explicit workflow") })
  }

  @Test
  fun `affected population needs approval and is then labelled as affected`() {
    val affected = record("affected", PopulationRole.AFFECTED_POPULATION, PopulationScope.WARD, 900)

    val blocked = PopulationDemandResolver.resolve(records = listOf(affected))
    assertNull(blocked.demand.people)
    assertTrue(blocked.rejected.any { it.reason.contains("workflow approval required") })

    val allowed = PopulationDemandResolver.resolve(
      records = listOf(affected),
      policy = PopulationDemandPolicy(allowAffectedAsDemand = true)
    )
    assertEquals(900, allowed.demand.people)
    assertEquals(PopulationRole.AFFECTED_POPULATION, allowed.demand.derivedFromRole)
    assertEquals(PopulationRole.AFFECTED_POPULATION, allowed.selected?.role)
  }

  // ----------------------------------------------------- simulated & missing

  @Test
  fun `simulated demand is used only when nothing real exists and stays simulated`() {
    val simulated = record(
      "demo", PopulationRole.RELOCATION_DEMAND, PopulationScope.WARD, 250,
      classification = PopulationClassification.SIMULATED,
      sourceKind = PopulationSourceKind.DEMO_SIMULATED
    )
    val withReal = PopulationDemandResolver.resolve(
      records = listOf(
        simulated,
        record("verified", PopulationRole.RELOCATION_DEMAND, PopulationScope.WARD, 380)
      )
    )
    assertEquals("verified", withReal.selected?.id)

    val demoOnly = PopulationDemandResolver.resolve(records = listOf(simulated))
    assertEquals(250, demoOnly.demand.people)
    assertEquals(ResourceDataState.SIMULATED, demoOnly.demand.state)
    assertTrue(demoOnly.statusLabel.startsWith("SIMULATED"))
    assertTrue(demoOnly.assumptions.any { it.contains("SIMULATED", ignoreCase = true) })
  }

  @Test
  fun `a record without a value is rejected rather than read as zero`() {
    val resolution = PopulationDemandResolver.resolve(
      records = listOf(
        record("no-value", PopulationRole.RELOCATION_DEMAND, PopulationScope.WARD, null),
        record("zero", PopulationRole.RELOCATION_DEMAND, PopulationScope.WARD, 0),
        record("negative", PopulationRole.RELOCATION_DEMAND, PopulationScope.WARD, -10)
      )
    )
    assertNull(resolution.demand.people)
    assertEquals(3, resolution.rejected.size)
    assertTrue(resolution.rejected.any { it.reason.contains("No value supplied") })
    assertTrue(resolution.rejected.any { it.reason.contains("not a usable population") })
  }

  @Test
  fun `an unclassified record is refused instead of guessed`() {
    val resolution = PopulationDemandResolver.resolve(
      records = listOf(
        record(
          "unclassified",
          PopulationRole.RELOCATION_DEMAND,
          PopulationScope.WARD,
          500,
          classification = PopulationClassification.NOT_PROVIDED
        )
      )
    )
    assertNull(resolution.demand.people)
    assertTrue(resolution.rejected.any { it.reason.contains("no classification") })
  }

  @Test
  fun `nothing available yields an explainable insufficient-data demand`() {
    val resolution = PopulationDemandResolver.resolve(records = emptyList())
    assertEquals(ResourceDataState.NOT_PROVIDED, resolution.demand.state)
    assertNull(resolution.selected)
    assertFalse(resolution.isAvailable)
    assertNotNull(resolution.demand.basis)
    assertTrue(resolution.demand.basis.contains("no relocation demand is stated"))
    assertTrue(resolution.demand.notes.any { it.contains("census/relief-registry") })
  }

  // ------------------------------------------------------ aggregation model

  @Test
  fun `the assessment keeps baseline, affected and demand apart`() {
    val records = listOf(
      record("baseline", PopulationRole.BASELINE_TOTAL, PopulationScope.VILLAGE, 12_000,
        sourceKind = PopulationSourceKind.OFFICIAL_CENSUS),
      record("affected", PopulationRole.AFFECTED_POPULATION, PopulationScope.WARD, 900),
      record("demand", PopulationRole.RELOCATION_DEMAND, PopulationScope.WARD, 380)
    )
    val resolution = PopulationDemandResolver.resolve(records = records)
    val assessment = PopulationAssessment.of(records, resolution)
    assertEquals(12_000, assessment.baseline?.value)
    assertEquals(900, assessment.affected?.value)
    assertEquals(380, assessment.relocationDemand?.value)
    assertEquals("380", assessment.demandLabel)
    assertTrue(assessment.baselineLabel.contains("not confirmed relocation demand"))
    assertTrue(assessment.affectedLabel.contains("not confirmed relocation demand"))
    // A verified demand is attributed, never assumed.
    assertEquals(com.example.data.model.DataClassification.OBSERVED, assessment.provenance?.classification)
    assertTrue(assessment.provenance?.isVerified == true)
  }

  @Test
  fun `an approved baseline demand is reported as derived, not observed`() {
    val records = listOf(
      record("baseline", PopulationRole.BASELINE_TOTAL, PopulationScope.VILLAGE, 12_000,
        sourceKind = PopulationSourceKind.OFFICIAL_CENSUS)
    )
    val resolution = PopulationDemandResolver.resolve(
      records = records,
      policy = PopulationDemandPolicy(allowBaselineAsDemand = true)
    )
    val assessment = PopulationAssessment.of(records, resolution)
    assertEquals(
      com.example.data.model.DataClassification.DERIVED,
      assessment.provenance?.classification
    )
    assertTrue(assessment.provenance?.isVerified == true)
  }

  @Test
  fun `a user declaration is never attributed as verified`() {
    val resolution = PopulationDemandResolver.resolve(
      records = emptyList(),
      householdDeclaration = household
    )
    val assessment = PopulationAssessment.of(emptyList(), resolution)
    assertEquals(
      com.example.data.model.DataClassification.CONFIGURED,
      assessment.provenance?.classification
    )
    assertFalse(assessment.provenance?.isVerified == true)
    assertEquals(PopulationScope.HOUSEHOLD, resolution.demand.scope)
    assertEquals(household, assessment.relocationDemand)
  }

  @Test
  fun `with no source at all there is nothing to attribute`() {
    val resolution = PopulationDemandResolver.resolve(records = emptyList())
    val assessment = PopulationAssessment.of(emptyList(), resolution)
    assertNull(assessment.provenance)
    assertNull(assessment.baseline)
    assertNull(assessment.affected)
    assertNull(assessment.relocationDemand)
    assertEquals("Not available", assessment.demandLabel)
    assertEquals("Not provided — no census source connected", assessment.baselineLabel)
  }

  @Test
  fun `a household declaration wins only as the last resort`() {
    val resolution = PopulationDemandResolver.resolve(
      records = emptyList(),
      householdDeclaration = household
    )
    assertEquals(5, resolution.demand.people)
    assertEquals(PopulationClassification.USER_DECLARED, resolution.demand.classification)
    assertEquals(ResourceDataState.USER_DECLARED, resolution.demand.state)
    assertEquals(PopulationScope.HOUSEHOLD, resolution.demand.scope)
    assertTrue(resolution.statusLabel.startsWith("USER_DECLARED"))
    assertTrue(resolution.demand.roleLabel.contains("not a census figure"))
  }
}
