package com.example

import com.example.data.disaster.DisasterSource
import com.example.data.disaster.DisasterType
import com.example.data.disaster.EventConfidence
import com.example.data.disaster.EventDetails
import com.example.data.disaster.EventGeometry
import com.example.data.disaster.EventOrigin
import com.example.data.disaster.deserializeEvent
import com.example.data.disaster.serializeEvent
import com.example.data.model.HazardSeverity
import com.example.data.routing.GeoPoint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the on-device disaster cache: every supported
 * EventDetails payload and every geometry kind must survive
 * serializeEvent -> deserializeEvent without losing identity, provenance,
 * timestamps or location. Cache shards written by older app versions must
 * still load (legacy compatibility), and shards that cannot be located
 * honestly must be dropped rather than pinned to a fabricated coordinate.
 */
class DisasterCacheRoundTripTest {

  private fun quakeEvent() = com.example.data.disaster.DisasterEvent(
    id = "usgs-us6000abcd",
    source = DisasterSource.USGS,
    sourceEventId = "us6000abcd",
    disasterType = DisasterType.EARTHQUAKE,
    title = "M 5.6 Earthquake — Andaman Islands",
    description = "Magnitude 5.6 earthquake detected by USGS.",
    geometry = EventGeometry.Point(12.5, 92.75),
    latitude = 12.5,
    longitude = 92.75,
    severity = HazardSeverity.HIGH,
    confidence = EventConfidence.NOT_PROVIDED,
    observedAtMillis = 1_800_000_000_000L,
    updatedAtMillis = 1_800_000_000_000L,
    origin = EventOrigin.OBSERVED,
    url = "https://earthquake.usgs.gov/earthquakes/eventpage/us6000abcd",
    details = EventDetails.Quake(
      magnitude = 5.6,
      depthKm = 33.5,
      place = "Andaman Islands",
      url = "https://earthquake.usgs.gov/earthquakes/eventpage/us6000abcd"
    )
  )

  private fun fireEvent() = com.example.data.disaster.DisasterEvent(
    id = "firms-2026-09-14T0315-27.1-95.3",
    source = DisasterSource.NASA_FIRMS,
    sourceEventId = "firms-2026-09-14T0315-27.1-95.3",
    disasterType = DisasterType.WILDFIRE,
    title = "Active Fire Detection",
    description = "Satellite fire/hotspot detection from NASA FIRMS.",
    geometry = EventGeometry.Point(27.1, 95.3),
    latitude = 27.1,
    longitude = 95.3,
    severity = HazardSeverity.MODERATE,
    confidence = EventConfidence.NOMINAL,
    confidenceNote = "detection confidence n",
    observedAtMillis = 1_800_500_000_000L,
    updatedAtMillis = 1_800_500_000_000L,
    origin = EventOrigin.OBSERVED,
    details = EventDetails.Fire(
      satellite = "Suomi-NPP",
      instrument = "VIIRS",
      frpMegawatts = 14.7,
      dayNight = "N"
    )
  )

  private fun alertEvent() = com.example.data.disaster.DisasterEvent(
    id = "imd-cap-2026-09-14-01",
    source = DisasterSource.IMD_CAP,
    sourceEventId = "2026-09-14-01",
    disasterType = DisasterType.HEAVY_RAINFALL,
    title = "Extremely Heavy Rainfall Warning — Kerala",
    description = "Official IMD CAP alert for Kerala districts.",
    geometry = EventGeometry.Polygon(
      listOf(
        GeoPoint(9.5, 76.2),
        GeoPoint(9.5, 77.4),
        GeoPoint(10.4, 77.4),
        GeoPoint(10.4, 76.2)
      )
    ),
    severity = HazardSeverity.HIGH,
    observedAtMillis = 1_800_100_000_000L,
    updatedAtMillis = 1_800_100_000_000L,
    expiresAtMillis = 1_800_200_000_000L,
    origin = EventOrigin.OBSERVED,
    affectedAreaLabel = "Idukki, Kottayam districts",
    url = "https://cap-sources.s3.amazonaws.com/in-imd-en/example.xml",
    details = EventDetails.OfficialAlert(
      event = "Extremely heavy rainfall",
      urgency = "Expected",
      certainty = "Likely",
      senderName = "India Meteorological Department",
      instruction = "Avoid low-lying areas",
      webLink = "https://mausam.imd.gov.in"
    )
  )

  @Test
  fun `quake round-trips identity provenance and details`() {
    val original = quakeEvent()
    val restored = deserializeEvent(serializeEvent(original))
    assertNotNull(restored)
    restored!!
    assertEquals(original.id, restored.id)
    assertEquals(original.source, restored.source)
    assertEquals(original.sourceEventId, restored.sourceEventId)
    assertEquals(original.disasterType, restored.disasterType)
    assertEquals(original.title, restored.title)
    assertEquals(original.severity, restored.severity)
    assertEquals(original.observedAtMillis, restored.observedAtMillis)
    assertEquals(original.updatedAtMillis, restored.updatedAtMillis)
    assertEquals(original.origin, restored.origin)
    assertEquals(original.url, restored.url)
    val g = restored.geometry as EventGeometry.Point
    assertEquals(12.5, g.lat, 1e-9)
    assertEquals(92.75, g.lon, 1e-9)
    val d = restored.details as EventDetails.Quake
    assertEquals(5.6, d.magnitude, 1e-9)
    assertEquals(33.5, d.depthKm, 1e-9)
    assertEquals("Andaman Islands", d.place)
    assertEquals((original.details as EventDetails.Quake).url, d.url)
  }

  @Test
  fun `fire round-trips satellite details and confidence`() {
    val restored = deserializeEvent(serializeEvent(fireEvent()))
    assertNotNull(restored)
    restored!!
    assertEquals(EventConfidence.NOMINAL, restored.confidence)
    assertEquals("detection confidence n", restored.confidenceNote)
    val d = restored.details as EventDetails.Fire
    assertEquals("Suomi-NPP", d.satellite)
    assertEquals("VIIRS", d.instrument)
    assertEquals(14.7, d.frpMegawatts!!, 1e-9)
    assertEquals("N", d.dayNight)
  }

  @Test
  fun `official alert round-trips polygon geometry and alert fields`() {
    val restored = deserializeEvent(serializeEvent(alertEvent()))
    assertNotNull(restored)
    restored!!
    val ring = (restored.geometry as EventGeometry.Polygon).ring
    assertEquals(4, ring.size)
    assertEquals(9.5, ring[0].lat, 1e-9)
    assertEquals(77.4, ring[2].lon, 1e-9)
    assertEquals("Idukki, Kottayam districts", restored.affectedAreaLabel)
    assertEquals(1_800_200_000_000L, restored.expiresAtMillis)
    val d = restored.details as EventDetails.OfficialAlert
    assertEquals("India Meteorological Department", d.senderName)
    assertEquals("Avoid low-lying areas", d.instruction)
  }

  @Test
  fun `user incident round-trips reporter note`() {
    val report = com.example.data.disaster.DisasterEvent(
      id = "user-1726300000000",
      source = DisasterSource.USER_REPORT,
      sourceEventId = "user-1726300000000",
      disasterType = DisasterType.LANDSLIDE,
      title = "Landslide",
      description = "Trees down across the ghat road.",
      geometry = EventGeometry.Point(9.93, 77.10),
      latitude = 9.93,
      longitude = 77.10,
      severity = HazardSeverity.HIGH,
      observedAtMillis = 1_800_300_000_000L,
      updatedAtMillis = 1_800_300_000_000L,
      origin = EventOrigin.REPORTED,
      details = EventDetails.UserIncident(
        categoryLabel = "Landslide",
        reporterNote = "Trees down across the ghat road."
      )
    )
    val restored = deserializeEvent(serializeEvent(report))
    assertNotNull(restored)
    restored!!
    assertEquals(EventOrigin.REPORTED, restored.origin)
    assertEquals(DisasterSource.USER_REPORT, restored.source)
    val d = restored.details as EventDetails.UserIncident
    assertEquals("Landslide", d.categoryLabel)
    assertEquals("Trees down across the ghat road.", d.reporterNote)
  }

  @Test
  fun `multipoint line and raster geometries round-trip`() {
    val multi = com.example.data.disaster.DisasterEvent(
      id = "multi-1", source = DisasterSource.USGS, sourceEventId = "multi-1",
      disasterType = DisasterType.OTHER, title = "t", description = "d",
      geometry = EventGeometry.MultiPoint(listOf(GeoPoint(10.0, 76.0), GeoPoint(11.0, 77.0))),
      severity = HazardSeverity.LOW, observedAtMillis = 1L, updatedAtMillis = 1L,
      origin = EventOrigin.OBSERVED
    )
    val line = multi.copy(
      id = "line-1", sourceEventId = "line-1",
      geometry = EventGeometry.Line(listOf(GeoPoint(10.0, 76.0), GeoPoint(11.0, 77.0)))
    )
    val raster = multi.copy(
      id = "raster-1", sourceEventId = "raster-1",
      geometry = EventGeometry.RasterLayer("VIIRS_Combined_Flood_1-Day", "Flood Extent (VIIRS)")
    )

    val multiRestored = deserializeEvent(serializeEvent(multi))!!
    val lineRestored = deserializeEvent(serializeEvent(line))!!
    val rasterRestored = deserializeEvent(serializeEvent(raster))!!

    val mp = multiRestored.geometry as EventGeometry.MultiPoint
    assertEquals(2, mp.points.size)
    assertEquals(11.0, mp.points[1].lat, 1e-9)
    assertEquals(77.0, mp.points[1].lon, 1e-9)
    val ln = lineRestored.geometry as EventGeometry.Line
    assertEquals(2, ln.points.size)
    val rl = rasterRestored.geometry as EventGeometry.RasterLayer
    assertEquals("VIIRS_Combined_Flood_1-Day", rl.layerId)
    assertEquals("Flood Extent (VIIRS)", rl.title)
  }

  @Test
  fun `degenerate polygon falls back to stored point columns when present`() {
    val original = alertEvent().copy(latitude = 9.95, longitude = 76.8)
    val json = serializeEvent(original)
    json.put("polygon", org.json.JSONArray()) // corrupt the ring on disk
    val restored = deserializeEvent(json)
    assertNotNull(restored)
    val g = restored!!.geometry
    assertTrue(g is EventGeometry.Point)
    // Stored point columns (a real persisted location), not invented ones.
    assertEquals(9.95, (g as EventGeometry.Point).lat, 1e-9)
    assertEquals(76.8, (g as EventGeometry.Point).lon, 1e-9)
  }

  @Test
  fun `unlocatable corrupted geometry shard is dropped not pinned to a fake spot`() {
    // Polygon event with NO stored point columns and a corrupted ring: there
    // is nowhere honest to put it, so it must be dropped entirely.
    val json = serializeEvent(alertEvent())
    json.put("polygon", org.json.JSONArray())
    assertNull(deserializeEvent(json))
  }

  @Test
  fun `legacy shard without details recovers quake magnitude honestly`() {
    val original = quakeEvent()
    val json = serializeEvent(original)
    json.remove("details")
    json.put("magnitude", 5.6) // what the original broken attempt wrote
    val restored = deserializeEvent(json)
    assertNotNull(restored)
    val d = restored!!.details
    assertTrue(d is EventDetails.Quake)
    assertEquals(5.6, (d as EventDetails.Quake).magnitude, 1e-9)
  }

  @Test
  fun `legacy non-quake shard without details stays Generic`() {
    val original = fireEvent()
    val json = serializeEvent(original)
    json.remove("details")
    val restored = deserializeEvent(json)
    assertNotNull(restored)
    assertEquals(EventDetails.Generic, restored!!.details)
  }

  @Test
  fun `shard missing source or sourceEventId is dropped`() {
    val broken = JSONObject().put("id", "x").put("title", "t")
    assertNull(deserializeEvent(broken))
  }
}
