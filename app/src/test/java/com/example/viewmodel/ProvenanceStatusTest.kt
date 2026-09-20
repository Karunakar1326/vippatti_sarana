package com.example.viewmodel

import com.example.data.model.DataStatus
import com.example.data.model.Freshness
import com.example.data.model.RecordStamp
import com.example.data.model.freshnessOf
import com.example.data.model.statusOf
import com.example.data.disaster.PilotRegionData
import com.example.data.disaster.cacheNote
import com.example.data.disaster.dataStatus
import com.example.data.disaster.recordStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 2 — provenance-model contracts.
 *
 * The status vocabulary is honest by construction: a record is only ever LIVE
 * when it has a real observed time, a simulated record is never LIVE or
 * VERIFIED, and missing timestamps say "unknown" rather than implying freshness.
 */
class ProvenanceStatusTest {

  private val liveProvenance = com.example.data.model.DataProvenance(
    source = "USGS Earthquake Hazards Program",
    status = com.example.data.model.DataProvenance.STATUS_LIVE,
    confidence = 0.9,
    isVerified = true,
    classification = com.example.data.model.DataClassification.OBSERVED
  )

  private val simulatedProvenance = com.example.data.model.DataProvenance(
    source = "Vippatti Sarana simulated network",
    status = com.example.data.model.DataProvenance.STATUS_FIELD,
    isVerified = false,
    classification = com.example.data.model.DataClassification.SIMULATED
  )

  private val now = 1_700_000_000_000L

  @Test
  fun `a verified live record with a real observed time is SUCCESS`() {
    val stamp = statusOf(
      provenance = liveProvenance,
      eventAtMillis = now - 60_000L
    )
    assertEquals(DataStatus.SUCCESS, stamp.status)
    assertEquals("USGS Earthquake Hazards Program", stamp.sourceName)
  }

  @Test
  fun `a simulated record is never LIVE`() {
    val stamp = statusOf(provenance = simulatedProvenance, eventAtMillis = now - 60_000L)
    assertEquals(DataStatus.SIMULATED, stamp.status)
    assertTrue(stamp.status != DataStatus.SUCCESS && stamp.status != DataStatus.VERIFIED)
  }

  @Test
  fun `an explicit provider failure is ERROR and carries the reason`() {
    val stamp = statusOf(
      provenance = liveProvenance,
      eventAtMillis = null,
      hasError = true,
      errorMessage = "No connection to the earthquake service."
    )
    assertEquals(DataStatus.ERROR, stamp.status)
    assertEquals("No connection to the earthquake service.", stamp.errorMessage)
  }

  @Test
  fun `missing timestamps mean unknown freshness, never fresh`() {
    assertEquals(Freshness.UNKNOWN, freshnessOf(null, now))
    assertEquals(Freshness.UNKNOWN, freshnessOf(0L, now))
    assertEquals(Freshness.FRESH, freshnessOf(now - 60_000L, now))
    assertEquals(Freshness.STALE, freshnessOf(now - 60L * 60L * 1000L, now))
  }

  @Test
  fun `age labels say unknown instead of inventing a time`() {
    val stamp = RecordStamp(
      sourceName = "IMD CAP",
      status = DataStatus.SUCCESS,
      eventAtMillis = null,
      retrievedAtMillis = null
    )
    assertEquals("Time unknown", stamp.eventAgeLabel(now))
    assertEquals("Time unknown", stamp.retrievedAgeLabel(now))
  }

  // ---------------------------------------------------------------- providers

  private fun providerState(
    source: com.example.data.disaster.DisasterSource = com.example.data.disaster.DisasterSource.USGS,
    eventCount: Int = 3,
    fetchedAtMillis: Long = now - 60_000L,
    isFromCache: Boolean = false,
    isLive: Boolean = true,
    statusMessage: String? = null,
    failureKind: com.example.data.disaster.ProviderFailureKind? = null
  ) = com.example.data.disaster.ProviderState(
    source = source,
    eventCount = eventCount,
    fetchedAtMillis = fetchedAtMillis,
    isFromCache = isFromCache,
    isLive = isLive,
    statusMessage = statusMessage,
    failureKind = failureKind
  )

  @Test
  fun `a provider fetched live this session is SUCCESS`() {
    val state = providerState()
    assertEquals(DataStatus.SUCCESS, state.dataStatus())
    assertEquals(null, state.cacheNote)
    val stamp = state.recordStamp()
    assertEquals("USGS Earthquake Hazards Program", stamp.sourceName)
    assertEquals(now - 60_000L, stamp.retrievedAtMillis)
  }

  @Test
  fun `a cache hit is never SUCCESS even inside the freshness window`() {
    val state = providerState(isFromCache = true, isLive = true)
    assertEquals(DataStatus.STALE, state.dataStatus())
    assertEquals("cache within its freshness window", state.cacheNote)
  }

  @Test
  fun `a failed provider with usable cached events is STALE and keeps the reason`() {
    val state = providerState(
      isFromCache = true,
      isLive = false,
      statusMessage = "HTTP 503 from the earthquake service"
    )
    assertEquals(DataStatus.STALE, state.dataStatus())
    assertEquals("cached events, live source unreachable", state.cacheNote)
    assertEquals("HTTP 503 from the earthquake service", state.recordStamp().errorMessage)
  }

  @Test
  fun `a failed provider with nothing cached is ERROR`() {
    // A real failure carries its kind; without a kind the record is a failure
    // that the provider did not classify, which still cannot read as live.
    val state = providerState(
      eventCount = 0,
      fetchedAtMillis = 0L,
      isFromCache = false,
      isLive = false,
      statusMessage = "FIRMS returned an error page instead of fire data.",
      failureKind = com.example.data.disaster.ProviderFailureKind.FAILED
    )
    assertEquals(DataStatus.ERROR, state.dataStatus())
    assertEquals(
      "FIRMS returned an error page instead of fire data.",
      state.recordStamp().errorMessage
    )

    // The same provider without a credential is UNCONFIGURED, not ERROR.
    val unconfigured = providerState(
      eventCount = 0,
      fetchedAtMillis = 0L,
      isFromCache = false,
      isLive = false,
      statusMessage = "FIRMS_MAP_KEY not configured",
      failureKind = com.example.data.disaster.ProviderFailureKind.UNCONFIGURED
    )
    assertEquals(DataStatus.NOT_CONFIGURED, unconfigured.dataStatus())
    assertEquals("FIRMS_MAP_KEY not configured", unconfigured.recordStamp().errorMessage)
    // A shard with no fetch time must not present a retrieval age.
    assertEquals(null, state.recordStamp().retrievedAtMillis)
  }

  @Test
  fun `cached news is never labelled ONLINE`() {
    val cached = VippattiUiState(
      newsArticles = listOf(article("Landslide reported in Idukki")),
      isNewsFromCache = true
    )
    assertEquals(DataStatus.STALE, cached.newsStatus)
    assertTrue(cached.newsConnectionStateLabel.startsWith("OFFLINE"))
    assertFalse(cached.newsConnectionStateLabel.contains("ONLINE"))
  }

  @Test
  fun `an empty or failed news feed is not claimed as a live feed`() {
    val empty = VippattiUiState()
    assertEquals(DataStatus.EMPTY, empty.newsStatus)
    assertEquals("NOT SYNCED", empty.newsConnectionStateLabel)
    val failed = VippattiUiState(
      newsError = com.example.data.news.NewsError(
        kind = com.example.data.news.NewsErrorKind.NETWORK,
        userMessage = "No connection to the news service."
      )
    )
    assertEquals(DataStatus.ERROR, failed.newsStatus)
    assertTrue(failed.newsConnectionStateLabel.startsWith("ERROR"))
  }

  @Test
  fun `weather is only LIVE for a reading fetched in this session`() {
    assertTrue(
      VippattiUiState(
        weatherStatus = DataStatus.SUCCESS,
        weatherRetrievedAtMillis = now
      ).isWeatherLive
    )
    val stale = VippattiUiState(weatherStatus = DataStatus.STALE, weatherRetrievedAtMillis = now)
    assertFalse(stale.isWeatherLive)
    assertEquals(DataStatus.LOADING, VippattiUiState().weatherStatus)
  }

  private fun article(title: String) = com.example.data.news.NewsArticle(
    id = "1",
    title = title,
    description = "",
    content = "",
    url = "https://example.invalid/1",
    imageUrl = null,
    publishedAtIso = "2026-09-14T04:44:31Z",
    publishedAtMillis = now - 3_600_000L,
    language = "en",
    sourceName = "Test wire",
    sourceUrl = "https://example.invalid",
    scope = com.example.data.news.NewsScope.MY_AREA,
    category = com.example.data.news.NewsCategory.GENERAL
  )

  @Test
  fun `the simulated demo network maps to SIMULATED status`() {
    // Every mock hazard carries DataClassification.SIMULATED provenance.
    PilotRegionData.hazardZones.forEach { zone ->
      val stamp = statusOf(provenance = zone.provenance, eventAtMillis = zone.lastUpdatedMillis)
      assertEquals(
        "mock zone ${zone.id} must not read as live",
        DataStatus.SIMULATED,
        stamp.status
      )
    }
  }
}