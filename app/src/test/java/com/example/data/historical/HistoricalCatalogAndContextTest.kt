package com.example.data.historical

import com.example.data.model.DataClassification
import com.example.data.model.DataStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HISTORICAL CATALOG + CONTEXT contracts.
 *
 * These pin the behaviour that keeps EM-DAT honest: area matching is
 * text-based (never geospatial inference), totals stay null rather than zero,
 * historical evidence never becomes a live hazard, and the context carries its
 * own limitations.
 */
class HistoricalCatalogAndContextTest {

  private fun event(
    id: String,
    year: Int = 2000,
    type: String = "Flood",
    country: String = "India",
    location: String? = "Idukki district (Kerala state)",
    admin: List<String> = emptyList(),
    deaths: Long? = null,
    affected: Long? = null,
    lat: Double? = null,
    lon: Double? = null,
    historicFlag: Boolean? = false
  ): HistoricalDisasterEvent {
    val precision = when {
      lat != null && lon != null -> HistoricalSpatialPrecision.SOURCE_COORDINATES
      admin.isNotEmpty() -> HistoricalSpatialPrecision.ADMIN_UNIT_ONLY
      location != null -> HistoricalSpatialPrecision.LOCATION_TEXT_ONLY
      else -> HistoricalSpatialPrecision.NOT_SUFFICIENT
    }
    return HistoricalDisasterEvent(
      id = id,
      historicFlag = historicFlag,
      group = "Natural",
      subgroup = "Hydrological",
      type = type,
      subtype = "Riverine flood",
      country = country,
      locationText = location,
      adminUnitNames = admin,
      startDate = HistoricalDate(year),
      impacts = HistoricalImpacts(totalDeaths = deaths, totalAffected = affected),
      latitude = lat,
      longitude = lon,
      source = "EM-DAT, CRED / UCLouvain, Brussels, Belgium",
      datasetVersion = "2026-09-11",
      spatialPrecision = precision
    )
  }

  private fun catalog(vararg events: HistoricalDisasterEvent) = HistoricalDisasterCatalog(
    events = events.toList(),
    info = HistoricalDatasetInfo(
      source = "EM-DAT, CRED / UCLouvain, Brussels, Belgium",
      version = "2026-09-11",
      declaredRecordCount = events.size,
      parsedRecordCount = events.size
    )
  )

  // ------------------------------------------------------------- filtering

  @Test
  fun `filters narrow by country type year range and area without mutating the source`() {
    val catalog = catalog(
      event("a", year = 1990, type = "Flood", location = "Idukki (Kerala)"),
      event("b", year = 2005, type = "Storm", location = "Odisha"),
      event("c", year = 2020, type = "Flood", country = "Nepal", location = "Kathmandu")
    )
    assertEquals(3, catalog.totalCount)

    assertEquals(listOf("a", "c"), catalog.apply(HistoricalFilters(type = "Flood")).map { it.id })
    assertEquals(listOf("c"), catalog.apply(HistoricalFilters(country = "Nepal")).map { it.id })
    assertEquals(
      listOf("a", "b"),
      catalog.apply(HistoricalFilters(yearFrom = 1990, yearTo = 2010)).map { it.id }
    )
    assertEquals(listOf("a"), catalog.apply(HistoricalFilters(areaText = "Idukki")).map { it.id })
    // The full catalog is untouched by filtering.
    assertEquals(3, catalog.totalCount)
    assertTrue(HistoricalFilters().description.contains("No filters"))
    assertTrue(HistoricalFilters(type = "Flood").isActive)
  }

  @Test
  fun `facets and trends are counted from the selection, sorted and deterministic`() {
    val catalog = catalog(
      event("a", year = 1990, type = "Flood"),
      event("b", year = 1993, type = "Flood"),
      event("c", year = 2001, type = "Storm")
    )
    assertEquals(listOf("Flood" to 2, "Storm" to 1), catalog.typeFacets())
    assertEquals(
      listOf(HistoricalTrendPoint(1990, 1), HistoricalTrendPoint(1993, 1), HistoricalTrendPoint(2001, 1)),
      catalog.yearlyTrend()
    )
    assertEquals(
      listOf(HistoricalTrendPoint(1990, 2), HistoricalTrendPoint(2000, 1)),
      catalog.decadeTrend()
    )
    assertEquals(IntRange(1990, 2001), catalog.yearRange)
    assertEquals("c", catalog.mostRecent()?.id)
  }

  @Test
  fun `an impact total is null when nothing in the selection reports it`() {
    val catalog = catalog(event("a"), event("b"))
    val summary = catalog.impactSummary()
    assertEquals(2, summary.eventCount)
    assertNull("no record states a death toll, so there is no total", summary.deathsTotal)
    assertEquals(0, summary.deathsReportingRecords)
    assertNull(summary.affectedTotal)
    assertTrue(HistoricalImpactSummary.EMPTY.isEmpty)
  }

  @Test
  fun `an impact total counts only the records that state it and reports coverage`() {
    val catalog = catalog(
      event("a", deaths = 10, affected = 100),
      event("b"),
      event("c", deaths = 5)
    )
    val summary = catalog.impactSummary()
    assertEquals(15L, summary.deathsTotal)
    assertEquals(2, summary.deathsReportingRecords)
    assertEquals("a and c state deaths, b does not", 100L, summary.affectedTotal)
    assertEquals(1, summary.affectedReportingRecords)
  }

  // -------------------------------------------------------- area matching

  @Test
  fun `area matching is text-based and rejects partial word hits`() {
    assertTrue(HistoricalAreaMatcher.sameArea("Idukki", "Idukki"))
    assertTrue(HistoricalAreaMatcher.sameArea("Idukki district", "Idukki"))
    assertTrue(HistoricalAreaMatcher.sameArea("Idukki district (Kerala state)", "Kerala"))
    // A needle that merely appears inside a longer word must not match.
    assertFalse(HistoricalAreaMatcher.sameArea("Idukkidualam", "Idukki"))
    assertFalse(HistoricalAreaMatcher.sameArea("Idukki", "Ida"))
    assertFalse(HistoricalAreaMatcher.sameArea("", "Idukki"))
    assertFalse(HistoricalAreaMatcher.sameArea("Idukki", "   "))
  }

  @Test
  fun `area tokens come from admin units and location text, deduplicated`() {
    val multiArea = event(
      id = "multi",
      location = "Idukki, Kottayam (Kerala state)",
      admin = listOf("Idukki", "Kerala")
    )
    assertTrue(multiArea.areaTokens.contains("Idukki"))
    assertTrue(multiArea.areaTokens.contains("Kottayam"))
    assertTrue(multiArea.areaTokens.contains("Kerala state"))
    assertEquals(multiArea.areaTokens.distinct(), multiArea.areaTokens)
  }

  // ------------------------------------------------------------- context

  @Test
  fun `context matches the resolved district and states how`() {
    val catalog = catalog(
      event("idukki-1", year = 2001, location = "Idukki, Kottayam (Kerala state)"),
      event("idukki-2", year = 2020, location = "Idukki district (Kerala state)"),
      event("other", year = 2018, location = "Odisha")
    )
    val context = HistoricalContextService.contextFor("Idukki", "Kerala", catalog)

    assertEquals(HistoricalMatchScope.DISTRICT, context.scope)
    assertEquals("Idukki (district)", context.areaLabel)
    assertEquals(2, context.eventCount)
    assertEquals(listOf("idukki-2", "idukki-1"), context.events.map { it.id })
    assertEquals(IntRange(2001, 2020), context.yearRange)
    assertTrue(context.matchMethod.contains("not a geospatial intersection"))
    assertTrue(context.limitations.contains(HistoricalContextService.DISCLAIMER))
    assertTrue(context.limitations.any { it.contains("no coordinates") })
    assertEquals(DataClassification.HISTORICAL, context.provenance?.classification)
    assertFalse(context.provenance?.isVerified ?: true)
    assertTrue(context.summaryLine.contains("2 historical records"))
  }

  @Test
  fun `context falls back to the state and says so`() {
    val catalog = catalog(event("kerala-only", location = "Kottayam (Kerala state)"))
    val context = HistoricalContextService.contextFor("Idukki", "Kerala", catalog)
    assertEquals(HistoricalMatchScope.STATE, context.scope)
    assertEquals("Kerala (state)", context.areaLabel)
    assertEquals(1, context.eventCount)
  }

  @Test
  fun `an unresolved area claims nothing`() {
    val catalog = catalog(event("x"))
    val context = HistoricalContextService.contextFor(null, null, catalog)
    assertEquals(HistoricalMatchScope.NO_AREA, context.scope)
    assertNull(context.areaLabel)
    assertFalse(context.hasEvidence)
    assertTrue(context.summaryLine.contains("No historical record"))
    assertTrue(context.matchMethod.contains("could not be resolved"))
    assertNull(context.provenance)
  }

  @Test
  fun `no dataset loaded is stated as such, not as an empty history`() {
    val context = HistoricalContextService.contextFor("Idukki", "Kerala", null)
    assertEquals(HistoricalMatchScope.NO_AREA, context.scope)
    assertTrue(context.matchMethod.contains("No historical dataset is loaded"))
    assertTrue(context.limitations.any { it.contains("EM-DAT dataset") })
  }

  @Test
  fun `an area with no matching record is distinguished from a missing dataset`() {
    val catalog = catalog(event("other", location = "Odisha"))
    val context = HistoricalContextService.contextFor("Idukki", "Kerala", catalog)
    assertEquals(HistoricalMatchScope.NO_AREA, context.scope)
    assertFalse(context.hasEvidence)
    assertTrue(context.limitations.contains(HistoricalContextService.DISCLAIMER))
  }

  // ------------------------------------------------------- not live, ever

  @Test
  fun `historical data is classified and labelled as history, never as live`() {
    val event = event("a")
    assertEquals(DataClassification.HISTORICAL, event.classification)
    assertEquals("Historical", DataClassification.HISTORICAL.label)
    assertFalse(event.classification == DataClassification.OBSERVED)
    // The status vocabulary separates an archive from a live feed too.
    assertEquals("Historical", DataStatus.HISTORICAL.label)
    assertFalse(DataStatus.HISTORICAL == DataStatus.SUCCESS)
    assertTrue(
      HistoricalSpatialPrecision.NOT_SUFFICIENT.explanation.contains("kept as historical context")
    )
    assertFalse(HistoricalSpatialPrecision.ADMIN_UNIT_ONLY.canPlaceOnMap)
    assertTrue(HistoricalSpatialPrecision.SOURCE_COORDINATES.canPlaceOnMap)
  }

  @Test
  fun `a record without source coordinates can never be mappable`() {
    val noCoordinates = event("a", location = "Idukki", admin = listOf("Idukki"))
    assertFalse(noCoordinates.isMappable)
    val coordinates = event("b", lat = 9.9, lon = 77.1)
    assertTrue(coordinates.isMappable)
    // Even when marked as having coordinates, a missing value blocks mapping.
    val partial = coordinates.copy(longitude = null)
    assertFalse(partial.isMappable)
  }

  // --------------------------------------------------------- serialization

  @Test
  fun `a record round-trips through json with every null preserved`() {
    val original = event(
      id = "2020-0332-IND",
      year = 2020,
      type = "Mass movement (wet)",
      location = null,
      admin = listOf("Idukki"),
      deaths = 70,
      lat = null,
      lon = null,
      historicFlag = false
    )
    val decoded = HistoricalDisasterJson.decode(
      HistoricalDisasterJson.encode(original).toString()
    )
    assertEquals(original, decoded)
    assertNull(decoded!!.impacts.totalAffected)
    assertNull(decoded.latitude)
    assertNull(decoded.locationText)
    assertFalse(decoded.isMappable)
  }

  @Test
  fun `json never turns an empty impact into zero`() {
    val encoded = HistoricalDisasterJson.encode(event("a"))
    assertTrue(encoded.isNull("totalDeaths"))
    assertTrue(encoded.isNull("totalAffected"))
    assertTrue(encoded.isNull("damageThousandUsd"))
    val decoded = HistoricalDisasterJson.decodeObject(encoded)!!
    assertNull(decoded.impacts.totalDeaths)
    assertFalse(decoded.impacts.hasAny)
    val list = HistoricalDisasterJson.decodeAll(
      HistoricalDisasterJson.encodeAll(listOf(event("a"), event("b"))).toString()
    )
    assertEquals(listOf("a", "b"), list.map { it.id })
  }

  @Test
  fun `json decoding rejects unusable payloads instead of guessing`() {
    assertNull(HistoricalDisasterJson.decode("not json"))
    assertNull(HistoricalDisasterJson.decode("{}"))
    assertNull(HistoricalDisasterJson.decode("""{"id":"a","startYear":2000}"""))
    assertNull(
      HistoricalDisasterJson.decode(
        """{"id":"a","startYear":2000,"classification":"MADE_UP"}"""
      )
    )
    assertTrue(HistoricalDisasterJson.decodeAll("{not json").isEmpty())
  }

  @Test
  fun `json cannot claim coordinates it does not carry`() {
    val payload = """
      {"id":"a","group":"Natural","subgroup":"Hydro","type":"Flood","subtype":"Riverine",
       "country":"India","startYear":2020,"classification":"HISTORICAL","source":"EM-DAT",
       "datasetVersion":"2026-09-11","spatialPrecision":"SOURCE_COORDINATES",
       "latitude":null,"longitude":null}
    """.trimIndent()
    val decoded = HistoricalDisasterJson.decode(payload)!!
    // The precision flag is downgraded rather than trusted blindly.
    assertEquals(HistoricalSpatialPrecision.NOT_SUFFICIENT, decoded.spatialPrecision)
    assertFalse(decoded.isMappable)
  }

  // ------------------------------------------------------------ provider

  @Test
  fun `the bundled provider loads a catalog and reports an empty file as a failure`() = runTest {
    val csv = "disno,group,subgroup,type,subtype,country,start_year,total_deaths," +
      "total_affected\n2000-0001-IND,Natural,Hydrological,Flood,Riverine flood,India,2000,5,\n"
    val loaded = BundledHistoricalDataProvider(
      reader = { name -> if (name.endsWith(".csv")) csv else "{\"Source\":\"EM-DAT\"}" },
      accessedOnProvider = { "2026-09-18" }
    ).load(nowMillis = 1_000L)

    val catalog = (loaded as HistoricalLoadResult.Loaded).catalog
    assertEquals(1, catalog.totalCount)
    assertEquals(1_000L, catalog.loadedAtMillis)
    assertEquals("2026-09-18", catalog.events[0].accessedOn)
    // The row states 5 deaths and leaves every other impact empty: the empty
    // ones stay null and are NEVER read as zero.
    assertEquals(5L, catalog.events[0].impacts.totalDeaths)
    assertNull(catalog.events[0].impacts.damageThousandUsd)
    assertNull(catalog.events[0].impacts.totalAffected)

    val blank = BundledHistoricalDataProvider(reader = { "" }).load(0L)
    assertTrue(blank is HistoricalLoadResult.Failed)

    val missing = BundledHistoricalDataProvider(reader = { null }).load(0L)
    assertTrue(missing is HistoricalLoadResult.Unavailable)
    assertTrue((missing as HistoricalLoadResult.Unavailable).reason.contains("No historical dataset"))

    val noColumns = BundledHistoricalDataProvider(reader = { "a,b\n1,2" }).load(0L)
    assertTrue(noColumns is HistoricalLoadResult.Failed)
    assertTrue((noColumns as HistoricalLoadResult.Failed).reason.contains("not usable"))

    val throwing = BundledHistoricalDataProvider(
      reader = { throw java.io.IOException("asset missing") }
    ).load(0L)
    assertTrue(throwing is HistoricalLoadResult.Failed)
    assertTrue((throwing as HistoricalLoadResult.Failed).reason.contains("asset missing"))
  }
}
