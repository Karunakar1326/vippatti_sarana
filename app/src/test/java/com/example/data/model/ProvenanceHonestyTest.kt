package com.example.data.model

import com.example.data.disaster.DisasterCachePolicy
import com.example.data.news.NewsCachePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STAGE 2 — provenance/freshness honesty contracts.
 *
 * Every behaviour changed in this stage is pinned here; no existing test is
 * weakened. Missing timestamps stay unknown (never fresh, never "recently"),
 * a verified record without a real observed time is never LIVE, and
 * future-dated shards never read as recent/usable.
 */
class ProvenanceHonestyTest {

  private val now = 1_800_000_000_000L

  private val verifiedObserved = DataProvenance(
    source = "USGS Earthquake Hazards Program",
    status = DataProvenance.STATUS_LIVE,
    confidence = 0.9,
    isVerified = true,
    classification = DataClassification.OBSERVED
  )

  // --- RecordStamp age labels: 0/negative means unknown, never "recently". ---

  @Test
  fun `a zero event time reads Time unknown, never Observed recently`() {
    val stamp = RecordStamp(sourceName = "USGS", eventAtMillis = 0L, status = DataStatus.SUCCESS)
    assertEquals("Time unknown", stamp.eventAgeLabel(now))
  }

  @Test
  fun `a negative event time reads Time unknown`() {
    val stamp = RecordStamp(sourceName = "USGS", eventAtMillis = -1000L, status = DataStatus.SUCCESS)
    assertEquals("Time unknown", stamp.eventAgeLabel(now))
  }

  @Test
  fun `a zero retrieval time reads Time unknown, never Fetched recently`() {
    val stamp = RecordStamp(sourceName = "USGS", retrievedAtMillis = 0L, status = DataStatus.SUCCESS)
    assertEquals("Time unknown", stamp.retrievedAgeLabel(now))
  }

  @Test
  fun `a real event time still renders an observed age`() {
    val stamp = RecordStamp(
      sourceName = "USGS",
      eventAtMillis = now - 60_000L,
      status = DataStatus.SUCCESS
    )
    assertTrue(stamp.eventAgeLabel(now).startsWith("Observed "))
  }

  // --- statusOf: LIVE requires a real observed time. ---

  @Test
  fun `a verified record with a real observed time is SUCCESS`() {
    val stamp = statusOf(provenance = verifiedObserved, eventAtMillis = now - 60_000L)
    assertEquals(DataStatus.SUCCESS, stamp.status)
  }

  @Test
  fun `a verified record with a null observed time is NOT_VERIFIED, never LIVE`() {
    val stamp = statusOf(provenance = verifiedObserved, eventAtMillis = null)
    assertEquals(DataStatus.NOT_VERIFIED, stamp.status)
    assertFalse(stamp.status == DataStatus.SUCCESS)
  }

  @Test
  fun `a verified record with a zero observed time is NOT_VERIFIED, never LIVE`() {
    val stamp = statusOf(provenance = verifiedObserved, eventAtMillis = 0L)
    assertEquals(DataStatus.NOT_VERIFIED, stamp.status)
    assertFalse(stamp.status == DataStatus.SUCCESS)
  }

  @Test
  fun `an explicit failure still maps to ERROR with the reason kept`() {
    val stamp = statusOf(
      provenance = verifiedObserved,
      eventAtMillis = now - 60_000L,
      hasError = true,
      errorMessage = "No connection to the earthquake service."
    )
    assertEquals(DataStatus.ERROR, stamp.status)
    assertEquals("No connection to the earthquake service.", stamp.errorMessage)
  }

  // --- DataProvenance.isFresh: missing time is never fresh. ---

  @Test
  fun `a provenance record with no timestamp is never fresh`() {
    assertFalse(DataProvenance(source = "field", lastUpdatedMillis = 0L).isFresh)
    assertFalse(DataProvenance(source = "field", lastUpdatedMillis = -5L).isFresh)
  }

  // --- Cache policies: future-dated shards are never recent/usable. ---

  @Test
  fun `a future disaster shard is not recent and not usable`() {
    val future = now + 60_000L
    assertFalse(DisasterCachePolicy.isFresh(future, now))
    assertFalse(DisasterCachePolicy.isRecent(future, now))
    assertFalse(DisasterCachePolicy.isUsable(future, now))
  }

  @Test
  fun `a fresh disaster shard is still fresh, recent and usable`() {
    val fresh = now - 60_000L
    assertTrue(DisasterCachePolicy.isFresh(fresh, now))
    assertTrue(DisasterCachePolicy.isRecent(fresh, now))
    assertTrue(DisasterCachePolicy.isUsable(fresh, now))
  }

  @Test
  fun `an expired disaster shard is not usable`() {
    val expired = now - DisasterCachePolicy.MAX_AGE_MILLIS - 1L
    assertFalse(DisasterCachePolicy.isUsable(expired, now))
    assertFalse(DisasterCachePolicy.isRecent(expired, now))
  }

  @Test
  fun `a future news shard is not usable stale`() {
    assertFalse(NewsCachePolicy.isUsableStale(now + 60_000L, now))
  }

  @Test
  fun `a recent news shard is still usable stale`() {
    assertTrue(NewsCachePolicy.isUsableStale(now - 60_000L, now))
  }
}
