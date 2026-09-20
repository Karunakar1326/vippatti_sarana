package com.example.data.historical

import com.example.data.model.DataClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * EM-DAT IMPORT - parser contracts, plus assertions against the REAL bundled
 * dataset.
 *
 * The integration half of this file does not use fixtures: it parses the
 * prepared CSV that actually ships in `app/src/main/assets/emdat/`, so the
 * numbers asserted here are the numbers the app will show. Every expectation was
 * read off the source file, not assumed.
 */
class EmdatImportTest {

  private val header =
    "disno,historic,group,subgroup,type,subtype,event_name,iso,country,location," +
      "admin_units,start_year,start_month,start_day,end_year,end_month,end_day," +
      "total_deaths,no_injured,no_affected,no_homeless,total_affected," +
      "damage_thousand_usd,damage_adjusted_thousand_usd,magnitude,magnitude_scale," +
      "latitude,longitude,entry_date,last_update"

  private fun csv(vararg rows: String) = (listOf(header) + rows).joinToString("\n")

  private fun row(
    disno: String = "2000-0001-IND",
    country: String = "India",
    location: String = "Idukki district (Kerala state)",
    admin: String = "",
    year: String = "2000",
    deaths: String = "",
    affected: String = "",
    lat: String = "",
    lon: String = "",
    type: String = "Flood",
    historic: String = "No"
  ) = listOf(
    disno, historic, "Natural", "Hydrological", type, "Riverine flood", "", "IND",
    country, location, admin, year, "", "", year, "", "", deaths, "", "", "",
    affected, "", "", "", "", lat, lon, "2020-01-01", "2026-05-12"
  ).joinToString(",")

  // =========================================================== REAL DATASET
  //
  // Facts below are read from the bundled file (EM-DAT version 2026-09-11,
  // 740 India records) - see PROJECT_STATE.md for the inspection report.

  private fun loadBundled(): EmdatCsvParser.Result {
    val candidates = listOf(
      File("src/main/assets/emdat/emdat_india_historical.csv"),
      File("app/src/main/assets/emdat/emdat_india_historical.csv")
    )
    val csvFile = candidates.firstOrNull { it.exists() }
      ?: error("bundled EM-DAT asset not found; looked in ${candidates.joinToString()}")
    val infoFile = File(csvFile.parentFile, "emdat_dataset_info.json")
    return EmdatCsvParser.parse(
      csvText = csvFile.readText(),
      infoJson = if (infoFile.exists()) infoFile.readText() else null,
      accessedOn = null
    )
  }

  @Test
  fun `the bundled dataset imports completely and idempotently`() {
    val first = loadBundled()
    val second = loadBundled()

    assertEquals(740, first.acceptedCount)
    assertTrue("no record should be rejected: ${first.rejected}", first.rejected.isEmpty())
    // Idempotent: the same file always yields the same catalog, in the same order.
    assertEquals(
      first.events.map { it.id },
      second.events.map { it.id }
    )
    assertEquals(first.events, second.events)
  }

  @Test
  fun `bundled attribution version coverage and countries are as the file states`() {
    val result = loadBundled()
    val events = result.events

    assertEquals(740, events.size)
    assertEquals(listOf("India"), events.map { it.country }.distinct())
    assertEquals(1900, events.minOf { it.startYear })
    assertEquals(2026, events.maxOf { it.startYear })
    // EM-DAT's own flags survive untouched.
    assertEquals(330, events.count { it.historicFlag == true })
    assertEquals(410, events.count { it.historicFlag == false })
    // Nine types exist in this export; the app must not invent a tenth.
    assertEquals(
      listOf(
        "Drought", "Earthquake", "Extreme temperature", "Flood",
        "Glacial lake outburst flood", "Mass movement (dry)",
        "Mass movement (wet)", "Storm", "Wildfire"
      ),
      events.map { it.type }.distinct().sorted()
    )
    assertEquals(
      "EM-DAT, CRED / UCLouvain, Brussels, Belgium",
      result.source
    )
    assertEquals("2026-09-11", result.version)
  }

  @Test
  fun `only the 94 records with their own coordinates are ever mappable`() {
    val events = loadBundled().events
    val mappable = events.filter { it.isMappable }
    assertEquals(94, mappable.size)
    assertEquals(646, events.size - mappable.size)

    // Every mappable record has BOTH coordinates and is flagged as such...
    assertTrue(mappable.all { it.latitude != null && it.longitude != null })
    assertTrue(
      mappable.all {
        it.spatialPrecision == HistoricalSpatialPrecision.SOURCE_COORDINATES
      }
    )
    // ...and no other record carries a fabricated or placeholder coordinate.
    val rest = events.filterNot { it.isMappable }
    assertTrue(rest.all { it.latitude == null && it.longitude == null })
    assertTrue(
      rest.all { it.spatialPrecision != HistoricalSpatialPrecision.SOURCE_COORDINATES }
    )
    // Coordinates are inside India-plausible bounds, i.e. really from the file.
    assertTrue(mappable.all { it.latitude!! in 3.0..37.0 && it.longitude!! in 68.0..98.0 })
  }

  @Test
  fun `missing impacts stay null and are never turned into zero`() {
    val events = loadBundled().events
    val withDeaths = events.count { it.impacts.totalDeaths != null }
    val withoutDeaths = events.count { it.impacts.totalDeaths == null }

    assertEquals(685, withDeaths)
    assertEquals(55, withoutDeaths)
    assertEquals(740, withDeaths + withoutDeaths)
    // The dataset never records a death toll of zero, and the import must not
    // invent one for the 55 records that simply do not state a figure.
    assertEquals(0, events.count { it.impacts.totalDeaths == 0L })
    assertEquals(0, events.count { it.impacts.deathsRecordedAsZero })
    // Seven records state no impact figure at all - they exist as evidence, and
    // their totals must stay absent rather than become 0.
    val noFigures = events.filterNot { it.impacts.hasAny }
    assertEquals(7, noFigures.size)
    assertTrue(noFigures.all { it.impacts.totalAffected == null })
    assertTrue(noFigures.all { it.impacts.damageThousandUsd == null })
  }

  @Test
  fun `impact totals use Long because the real sum overflows Int`() {
    val catalog = HistoricalDisasterCatalog(
      events = loadBundled().events,
      info = HistoricalDatasetInfo(source = "EM-DAT")
    )
    val summary = catalog.impactSummary()
    assertEquals(740, summary.eventCount)
    // Real figure: 2,530,696,266 affected people - larger than Int.MAX_VALUE
    // (2,147,483,647), so an Int total would silently wrap.
    assertTrue(
      "affected total must exceed Int range to prove Long is required",
      summary.affectedTotal!! > Int.MAX_VALUE.toLong()
    )
    assertEquals(2_530_696_266L, summary.affectedTotal)
    assertEquals(4_604_992L, summary.deathsTotal)
    assertEquals(685, summary.deathsReportingRecords)
  }

  @Test
  fun `every bundled record is classified HISTORICAL and none is presented as live`() {
    val events = loadBundled().events
    assertTrue(events.all { it.classification == DataClassification.HISTORICAL })
    assertFalse(events.any { it.classification == DataClassification.OBSERVED })
    assertTrue(events.all { it.source.isNotBlank() })
    assertTrue(events.all { it.datasetVersion == "2026-09-11" })
  }

  @Test
  fun `the pilot area records are the three known EM-DAT entries`() {
    val events = loadBundled().events
    val idukki = events.filter { HistoricalAreaMatcher.matches(it, "Idukki") }
    assertEquals(3, idukki.size)
    assertEquals(
      listOf("2001-0387-IND", "2020-0332-IND", "2021-0677-IND"),
      idukki.map { it.id }.sorted()
    )
    // None of them is mappable: EM-DAT gives no coordinates for the district.
    assertTrue(idukki.none { it.isMappable })
    assertEquals(49, events.count { HistoricalAreaMatcher.matches(it, "Kerala") })

    // 2020-0332 is the Idukki landslide: a real death toll, no other figures.
    val landslide = events.first { it.id == "2020-0332-IND" }
    assertEquals(70L, landslide.impacts.totalDeaths)
    assertNull(landslide.impacts.totalAffected)
    assertNull(landslide.impacts.homeless)
    assertEquals(HistoricalSpatialPrecision.ADMIN_UNIT_ONLY, landslide.spatialPrecision)
    assertTrue(landslide.adminUnitNames.contains("Idukki"))
  }

  // ============================================================ PARSER UNITS

  @Test
  fun `quoted location text with commas stays a single field`() {
    val result = EmdatCsvParser.parse(
      csv(
        row(disno = "2001-0387-IND", location = "\"Idukki, Kottayam, Kollam (Kerala state)\""),
        row(disno = "2002-0002-IND", location = "\"Assam, Bihar\"", deaths = "1200")
      )
    )
    assertEquals(2, result.acceptedCount)
    assertEquals("Idukki, Kottayam, Kollam (Kerala state)", result.events[0].locationText)
    assertEquals(1200L, result.events[1].impacts.totalDeaths)
  }

  @Test
  fun `escaped quotes in admin units json are read and malformed json is noted`() {
    val good = EmdatCsvParser.parse(
      csv(
        row(
          admin = "\"[{\"\"adm2_name\"\":\"\"Idukki\"\"},{\"\"adm1_name\"\":\"\"Kerala\"\"}]\"",
          location = "\"Idukki (Kerala)\""
        )
      )
    )
    assertEquals(listOf("Idukki", "Kerala"), good.events[0].adminUnitNames.sorted())
    assertEquals(
      HistoricalSpatialPrecision.ADMIN_UNIT_ONLY,
      good.events[0].spatialPrecision
    )

    val bad = EmdatCsvParser.parse(csv(row(admin = "\"[{,not json\"", location = "Idukki")))
    assertTrue(bad.events[0].adminUnitNames.isEmpty())
    assertTrue(
      bad.events[0].notes.any { it.contains("could not be read") }
    )
    // With no admin units it falls back to location text, not to coordinates.
    assertEquals(
      HistoricalSpatialPrecision.LOCATION_TEXT_ONLY,
      bad.events[0].spatialPrecision
    )
  }

  @Test
  fun `the placeholder admin unit is not treated as a real area`() {
    val result = EmdatCsvParser.parse(
      csv(
        row(
          admin = "\"[{\"\"adm1_name\"\":\"\"Administrative unit not available\"\"}]\"",
          location = "Orissa"
        )
      )
    )
    assertTrue(result.events[0].adminUnitNames.isEmpty())
    assertEquals(HistoricalSpatialPrecision.LOCATION_TEXT_ONLY, result.events[0].spatialPrecision)
  }

  @Test
  fun `a file missing required columns is rejected whole`() {
    val broken = "disno,country,start_year\n2000-0001-IND,India,2000"
    val error = runCatching { EmdatCsvParser.parse(broken) }.exceptionOrNull()
    assertNotNull(error)
    assertTrue(error!!.message!!.contains("missing required columns"))
  }

  @Test
  fun `duplicate identifiers are rejected with the reason and the first record is kept`() {
    val result = EmdatCsvParser.parse(
      csv(
        row(disno = "2000-0001-IND", deaths = "10"),
        row(disno = "2000-0001-IND", deaths = "20"),
        row(disno = "2000-0002-IND", deaths = "30")
      )
    )
    assertEquals(2, result.acceptedCount)
    assertEquals(1, result.rejected.size)
    assertEquals("2000-0001-IND", result.rejected[0].id)
    assertTrue(result.rejected[0].reason.contains("Duplicate identifier"))
    assertEquals(10L, result.events[0].impacts.totalDeaths)
  }

  @Test
  fun `records without an identifier or a usable year are rejected`() {
    val result = EmdatCsvParser.parse(
      csv(
        row(disno = ""),
        row(disno = "2000-0003-IND", year = ""),
        row(disno = "2000-0004-IND", year = "twelve"),
        row(disno = "2000-0005-IND", year = "1250"),
        row(disno = "2000-0006-IND", year = "2001")
      )
    )
    assertEquals(1, result.acceptedCount)
    assertEquals(4, result.rejected.size)
    assertEquals(
      listOf(
        "No EM-DAT identifier (DisNo.)",
        "Start year is missing or not a number",
        "Start year is missing or not a number",
        "Start year 1250 is outside a usable range"
      ),
      result.rejected.map { it.reason }
    )
  }

  @Test
  fun `dates keep only the precision the source states`() {
    val result = EmdatCsvParser.parse(
      csv(
        row(disno = "1987-0137-IND", year = "1987"),
        row(disno = "2020-0332-IND", year = "2020")
      )
    )
    val dated = result.events[0]
    assertEquals(1987, dated.startDate.year)
    assertNull("a year-only record must not gain a month", dated.startDate.month)
    assertNull(dated.startDate.day)
    assertEquals("1987", dated.startDate.label)
  }

  @Test
  fun `values that cannot be trusted are dropped with a note instead of wrapped`() {
    val result = EmdatCsvParser.parse(
      csv(
        row(deaths = "-5", affected = "not-a-number"),
        row(disno = "2000-0002-IND", lat = "999.0", lon = "77.0")
      )
    )
    val first = result.events[0]
    assertNull("a negative toll is not a population", first.impacts.totalDeaths)
    assertNull(first.impacts.totalAffected)
    assertTrue(first.notes.any { it.contains("negative") })
    assertTrue(first.notes.any { it.contains("unreadable") })

    val second = result.events[1]
    assertNull("an impossible latitude is dropped", second.latitude)
    // The valid longitude survives, but one coordinate is not enough to map.
    assertEquals(77.0, second.longitude!!, 0.0001)
    assertFalse(second.isMappable)
    assertTrue(second.notes.any { it.contains("outside the valid range") })
  }

  @Test
  fun `a record with only one coordinate is never treated as locatable`() {
    val result = EmdatCsvParser.parse(csv(row(lat = "10.0", lon = "")))
    val event = result.events[0]
    assertNull(event.longitude)
    assertFalse(event.isMappable)
    assertEquals(HistoricalSpatialPrecision.LOCATION_TEXT_ONLY, event.spatialPrecision)
    assertTrue(event.notes.any { it.contains("Only one coordinate") })
  }

  @Test
  fun `a trailing blank line is not a record`() {
    val result = EmdatCsvParser.parse(csv(row()) + "\n")
    assertEquals(1, result.acceptedCount)
    assertTrue(result.rejected.isEmpty())
  }

  @Test
  fun `a declared record count that disagrees with the file is surfaced`() {
    val info = """{"Source":"EM-DAT","Version":"2026-09-11","# of records":"999"}"""
    val result = EmdatCsvParser.parse(csv(row()), infoJson = info)
    assertEquals(1, result.acceptedCount)
    assertTrue(
      result.notes.any { it.contains("declares 999 records") && it.contains("1 were imported") }
    )
  }

  @Test
  fun `attribution without an info sheet names the source but claims no version`() {
    val result = EmdatCsvParser.parse(csv(row()))
    assertEquals(HistoricalDatasetInfo.UNKNOWN_SOURCE, result.source)
    assertNull(result.version)
    val event = result.events[0]
    assertEquals("not stated in dataset", event.datasetVersion)
  }
}
