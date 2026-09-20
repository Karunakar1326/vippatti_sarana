package com.example.data.population

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * POPULATION RECORD - wire-format contracts.
 *
 * The contract a census/registrar or disaster-authority feed will use. A
 * missing figure must survive the round trip as null, never as 0, and a record
 * whose meaning cannot be decoded must be rejected rather than reshaped.
 */
class PopulationRecordJsonTest {

  private val record = PopulationRecord(
    id = "ward-4-relocation-demand",
    role = PopulationRole.RELOCATION_DEMAND,
    scope = PopulationScope.WARD,
    areaName = "Ward 4",
    value = 380,
    classification = PopulationClassification.ESTIMATED,
    sourceKind = PopulationSourceKind.AUTHORITY_ASSESSMENT,
    source = "State disaster authority field assessment",
    referenceMillis = 1_790_000_000_000L,
    confidence = 0.72,
    notes = listOf("Collected by the ward officer.")
  )

  @Test
  fun `a record round-trips every provenance field`() {
    val decoded = PopulationRecordJson.decode(PopulationRecordJson.encode(record).toString())
    assertEquals(record, decoded)
  }

  @Test
  fun `a list round-trips in order`() {
    val second = record.copy(id = "village-baseline", role = PopulationRole.BASELINE_TOTAL)
    val encoded = PopulationRecordJson.encodeAll(listOf(record, second))
    val decoded = PopulationRecordJson.decodeAll(encoded.toString())
    assertEquals(listOf(record, second), decoded)
  }

  @Test
  fun `a missing value stays null and is never read as zero`() {
    val noValue = record.copy(value = null)
    val json = PopulationRecordJson.encode(noValue)
    assertTrue(json.isNull("value"))
    val decoded = PopulationRecordJson.decodeObject(json)
    assertNull(decoded?.value)
    assertFalse(decoded!!.hasValue)
  }

  @Test
  fun `unstated reference and confidence stay null`() {
    val unstated = record.copy(referenceMillis = null, confidence = null)
    val decoded = PopulationRecordJson.decodeObject(PopulationRecordJson.encode(unstated))
    assertNull(decoded?.referenceMillis)
    assertNull(decoded?.confidence)
    // The area name is still carried through untouched.
    assertEquals("Ward 4", decoded?.areaName)
  }

  @Test
  fun `absent reference and confidence keys decode to null, never epoch or NaN`() {
    val json = PopulationRecordJson.encode(record)
    json.remove("referenceMillis")
    json.remove("confidence")
    val decoded = PopulationRecordJson.decodeObject(json)
    assertNull(decoded?.referenceMillis)
    assertNull(decoded?.confidence)
    // The figure itself still survives untouched.
    assertEquals(380, decoded?.value)
  }

  @Test
  fun `a record with unstated confidence never outranks a stated confidence`() {
    val stated = record.copy(id = "stated", confidence = 0.9)
    val unstatedJson = PopulationRecordJson.encode(record.copy(id = "unstated"))
    unstatedJson.remove("confidence")
    val unstated = PopulationRecordJson.decodeObject(unstatedJson)!!
    assertNull(unstated.confidence)
    val resolution = PopulationDemandResolver.resolve(records = listOf(unstated, stated))
    assertEquals(
      "stated confidence must win the tie-break",
      "stated",
      resolution.selected?.id
    )
  }

  @Test
  fun `a record whose role or scope cannot be decoded is rejected`() {
    val json = PopulationRecordJson.encode(record)
    assertNull(PopulationRecordJson.decodeObject(json.put("role", "SOMETHING_ELSE")))
    assertNull(PopulationRecordJson.decodeObject(json.put("role", "RELOCATION_DEMAND").put("scope", "PLANET")))
    assertNull(PopulationRecordJson.decodeObject(json.put("scope", "WARD").put("id", "")))
  }

  @Test
  fun `a missing classification is never invented as verified`() {
    val decoded = PopulationRecordJson.decodeObject(
      PopulationRecordJson.encode(record).put("classification", "")
    )
    assertEquals(PopulationClassification.NOT_PROVIDED, decoded?.classification)
    // The source kind is an independent field and survives unchanged.
    assertEquals(PopulationSourceKind.AUTHORITY_ASSESSMENT, decoded?.sourceKind)
    assertEquals(380, decoded?.value)
  }

  @Test
  fun `malformed payloads decode to null or an empty list`() {
    assertNull(PopulationRecordJson.decode("{not json"))
    assertTrue(PopulationRecordJson.decodeAll("{not json").isEmpty())
    assertTrue(PopulationRecordJson.decodeAll("[]").isEmpty())
  }
}
