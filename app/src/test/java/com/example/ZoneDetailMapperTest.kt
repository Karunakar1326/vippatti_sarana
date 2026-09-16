package com.example

import com.example.data.PilotRegionData
import com.example.data.disaster.DisasterEvent
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.DisasterType
import com.example.data.disaster.EventConfidence
import com.example.data.disaster.EventDetails
import com.example.data.disaster.EventGeometry
import com.example.data.disaster.EventOrigin
import com.example.data.disaster.EventStatus
import com.example.data.disaster.IncidentCategory
import com.example.data.disaster.IncidentReport
import com.example.data.disaster.ZoneDetailMapper
import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Disaster-aware popup contracts: every zone renders ONLY its own type's
 * rows, missing backend fields surface as null (UI: "Data unavailable"),
 * the nearest viable safe zone is computed live, and switching zones swaps
 * content instead of retaining the previous zone's values.
 */
class ZoneDetailMapperTest {

  private val provenance = DataProvenance(
    source = "test",
    status = "FIELD RECORD",
    confidence = 0.6,
    isVerified = false,
    classification = DataClassification.SIMULATED
  )

  private fun zone(
    id: String,
    type: HazardType,
    name: String = "Test $type zone",
    center: GeoPoint = GeoPoint(20.0, 78.0),
    radius: Double = 2000.0
  ) = HazardZone(
    id = id,
    name = name,
    type = type,
    severity = HazardSeverity.HIGH,
    center = center,
    radiusMeters = radius,
    riskLevel = "ORANGE",
    trend = HazardTrend.STABLE,
    sourceStatus = "test source",
    lastUpdatedMillis = 0L,
    provenance = provenance
  )

  private fun labelsOf(zoneId: String, type: HazardType) =
    ZoneDetailMapper.map(
      zone = zone(zoneId, type),
      event = null,
      feasibleSafeZones = emptyList()
    ).sections.flatMap { it.fields.map { f -> f.label } }

  private fun quakeEvent() = DisasterEvent(
    id = "q1",
    source = DisasterSource.USGS,
    sourceEventId = "q1",
    disasterType = DisasterType.EARTHQUAKE,
    title = "M6.5 test quake",
    description = "",
    geometry = EventGeometry.Point(23.0, 70.0),
    severity = HazardSeverity.HIGH,
    confidence = EventConfidence.NOT_PROVIDED,
    observedAtMillis = 1_700_000_000_000L,
    updatedAtMillis = 1_700_000_000_000L,
    status = EventStatus.ACTIVE,
    origin = EventOrigin.OBSERVED,
    details = EventDetails.Quake(6.5, 12.0, "Test place", null)
  )

  // ------------------------------------------------------------ type purity

  @Test
  fun `flood shows only flood rows with unavailable water level`() {
    val detail = ZoneDetailMapper.map(zone("m1", HazardType.FLOOD), null, emptyList())
    assertEquals(listOf("FLOOD INTELLIGENCE"), detail.sections.map { it.heading })
    val fields = detail.sections.single().fields.associate { it.label to it.value }
    assertEquals("High", fields["Flood severity"])
    assertEquals("ORANGE", fields["Warning status"])
    assertNotNull(fields["Affected area"])
    assertNull(fields["Water level / flood extent"])
    assertNull(fields["Affected habitations"])
    val labels = fields.keys.joinToString(" ")
    assertTrue(!labels.contains("Magnitude"))
    assertTrue(!labels.contains("Avalanche"))
    assertTrue(!labels.contains("Wind speed"))
  }

  @Test
  fun `landslide shows only landslide rows`() {
    val labels = labelsOf("m2", HazardType.LANDSLIDE)
    assertTrue(labels.contains("Landslide severity"))
    assertTrue(labels.contains("Susceptible area"))
    assertTrue(labels.contains("Slope / terrain indicator"))
    assertTrue(labels.contains("Road / access status"))
    assertTrue(!labels.joinToString(" ").contains("Magnitude"))
    assertTrue(!labels.joinToString(" ").contains("Water level"))
  }

  @Test
  fun `heavy rainfall shows only rainfall rows`() {
    val detail = ZoneDetailMapper.map(zone("m3", HazardType.HEAVY_RAINFALL), null, emptyList())
    assertEquals(listOf("HEAVY RAINFALL INTELLIGENCE"), detail.sections.map { it.heading })
    val fields = detail.sections.single().fields.associate { it.label to it.value }
    assertNull(fields["Rainfall intensity"])
    assertEquals("High", fields["Warning level"])
    assertEquals("Stable", fields["Risk trend"])
  }

  @Test
  fun `cyclone shows only cyclone rows`() {
    val labels = labelsOf("m4", HazardType.CYCLONE)
    assertTrue(labels.contains("Cyclone / alert name"))
    assertTrue(labels.contains("Wind speed / gusts"))
    assertTrue(labels.contains("Direction / movement"))
    assertTrue(!labels.joinToString(" ").contains("Magnitude"))
    assertTrue(!labels.joinToString(" ").contains("Avalanche"))
  }

  @Test
  fun `earthquake with backend event shows magnitude depth time`() {
    val z = zone("live-USGS:q1", HazardType.EARTHQUAKE, center = GeoPoint(23.0, 70.0))
    val found = ZoneDetailMapper.findSourceEvent(z, listOf(quakeEvent()))
    assertNotNull(found)
    val fields = ZoneDetailMapper.map(z, found, emptyList())
      .sections.single().fields.associate { it.label to it.value }
    assertEquals("6.5", fields["Magnitude"])
    assertEquals("12.0 km", fields["Depth"])
    assertNotNull(fields["Time of event"])
    assertNotNull(fields["Epicenter"])
  }

  @Test
  fun `earthquake without backend event shows unavailable magnitude`() {
    val fields = ZoneDetailMapper.map(
      zone("m5", HazardType.EARTHQUAKE), null, emptyList()
    ).sections.single().fields.associate { it.label to it.value }
    assertNull(fields["Magnitude"])
    assertNull(fields["Depth"])
    assertNull(fields["Time of event"])
    assertNotNull(fields["Epicenter"]) // zone centre always exists
  }

  @Test
  fun `fire detection time follows the linked event`() {
    val fireEvent = quakeEvent().copy(
      id = "f1",
      sourceEventId = "f1",
      source = DisasterSource.NASA_FIRMS,
      disasterType = DisasterType.WILDFIRE,
      origin = EventOrigin.OBSERVED,
      details = EventDetails.Fire("Terra", "MODIS", 12.5, "D")
    )
    val z = zone("live-NASA_FIRMS:f1", HazardType.FIRE)
    val withEvent = ZoneDetailMapper.map(
      z,
      ZoneDetailMapper.findSourceEvent(z, listOf(fireEvent)),
      emptyList()
    ).sections.single().fields.associate { it.label to it.value }
    assertNotNull(withEvent["Detection time"])
    val withoutEvent = ZoneDetailMapper.map(
      zone("m6", HazardType.FIRE), null, emptyList()
    ).sections.single().fields.associate { it.label to it.value }
    assertNull(withoutEvent["Detection time"])
    assertNull(withoutEvent["Wind information"])
  }

  @Test
  fun `avalanche rows appear only for avalanche zones`() {
    val detail = ZoneDetailMapper.map(
      zone("m7", HazardType.OTHER, name = "High-Altitude Avalanche Watch"),
      null,
      emptyList()
    )
    assertEquals(listOf("AVALANCHE INTELLIGENCE"), detail.sections.map { it.heading })
    val labels = detail.sections.single().fields.map { it.label }
    assertTrue(labels.contains("Avalanche warning level"))
    assertTrue(!labels.joinToString(" ").contains("Magnitude"))
  }

  @Test
  fun `other disasters show only generic backend fields`() {
    val detail = ZoneDetailMapper.map(
      zone("m8", HazardType.OTHER, name = "Unknown hazard pocket"),
      null,
      emptyList()
    )
    val labels = detail.sections.single().fields.map { it.label }
    assertTrue(labels.containsAll(listOf("Disaster type", "Severity", "Risk level", "Affected area", "Location")))
    val joined = labels.joinToString(" ")
    listOf("Magnitude", "Avalanche", "Wind speed", "Water level", "Landslide severity", "Fire severity")
      .forEach { assertTrue("leaked $it", !joined.contains(it)) }
  }

  // ---------------------------------------------------------- safe zone

  @Test
  fun `nearest viable safe zone is computed live from the list`() {
    val shelters = PilotRegionData.safeZones
    val assam = PilotRegionData.hazardZones.first { it.id == "hz-flood-assam-dibrugarh" }
    val detail = ZoneDetailMapper.map(assam, null, shelters)
    val nearest = detail.nearestSafeZone
    assertNotNull(nearest)
    assertEquals("Dibrugarh University Relief Hall", nearest!!.name)
    assertTrue(nearest.capacityText.contains("spots free"))
  }

  @Test
  fun `no viable safe zone yields null instead of an invented shelter`() {
    val detail = ZoneDetailMapper.map(
      zone("m9", HazardType.FLOOD), null, emptyList()
    )
    assertNull(detail.nearestSafeZone)
  }

  // ---------------------------------------------------------- linking

  @Test
  fun `mock zones link to no backend event`() {
    val mock = PilotRegionData.hazardZones.first()
    assertNull(ZoneDetailMapper.findSourceEvent(mock, listOf(quakeEvent())))
  }

  @Test
  fun `citizen report zone links and appends the report section`() {
    val report = IncidentReport(
      id = "user-1",
      category = IncidentCategory.FLOODED_ROAD,
      description = "Water over the road",
      location = GeoPoint(20.0, 78.0),
      reportedAtMillis = 1_700_000_000_000L,
      severity = HazardSeverity.MODERATE,
      reporterName = "tester"
    )
    val event = report.toDisasterEvent(1_700_000_000_000L)
    val z = zone("live-${event.dedupeKey}", HazardType.FLOOD)
    val found = ZoneDetailMapper.findSourceEvent(z, emptyList(), listOf(event))
    assertNotNull(found)
    val detail = ZoneDetailMapper.map(z, found, emptyList())
    assertEquals(2, detail.sections.size)
    assertEquals("CITIZEN REPORT", detail.sections[1].heading)
  }

  // ---------------------------------------------------------- switching

  @Test
  fun `switching zones swaps content without retaining previous values`() {
    val flood = ZoneDetailMapper.map(zone("s1", HazardType.FLOOD), null, emptyList())
    val quake = ZoneDetailMapper.map(
      zone("s2", HazardType.EARTHQUAKE), quakeEvent(), emptyList()
    )
    assertEquals("FLOOD INTELLIGENCE", flood.sections.first().heading)
    assertEquals("EARTHQUAKE INTELLIGENCE", quake.sections.first().heading)
    val floodLabels = flood.sections.flatMap { it.fields.map { f -> f.label } }
    val quakeLabels = quake.sections.flatMap { it.fields.map { f -> f.label } }
    assertTrue(floodLabels.contains("Water level / flood extent"))
    assertTrue(!floodLabels.contains("Magnitude"))
    assertTrue(quakeLabels.contains("Magnitude"))
    assertTrue(!quakeLabels.contains("Water level / flood extent"))
  }
}
