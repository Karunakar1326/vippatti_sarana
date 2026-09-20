package com.example.data.disaster

import com.example.data.disaster.providers.CapAlertParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * PHASE 3 - official-alert geometry honesty.
 *
 * IMD CAP alerts are sometimes published without a `<polygon>`. Such an alert
 * is a real record with NO known location, so it must never be pinned to a
 * placeholder coordinate (the app used to place it at India's centre, which
 * read as a located hazard on the map).
 */
@RunWith(RobolectricTestRunner::class)
class CapAlertGeometryHonestyTest {

  private fun capXml(areaDesc: String, polygon: String?) = """
    <?xml version="1.0" encoding="UTF-8"?>
    <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
      <identifier>IMD-TEST-$areaDesc</identifier>
      <sender>imd@test</sender>
      <sent>2026-09-20T06:00:00+05:30</sent>
      <status>Actual</status>
      <msgType>Alert</msgType>
      <info>
        <category>Met</category>
        <event>Extremely heavy rainfall</event>
        <urgency>Immediate</urgency>
        <severity>Severe</severity>
        <certainty>Likely</certainty>
        <expires>2026-09-21T06:00:00+05:30</expires>
        <senderName>India Meteorological Department</senderName>
        <headline>Extremely heavy rainfall over the district</headline>
        <description>Official warning text.</description>
        <instruction>Follow district administration advice.</instruction>
        <area>
          <areaDesc>$areaDesc</areaDesc>
          ${polygon?.let { "<polygon>$it</polygon>" } ?: ""}
        </area>
        <web>https://cap-sources.s3.amazonaws.com/in-imd-en/example.xml</web>
      </info>
    </alert>
  """.trimIndent()

  @Test
  fun `an alert without a polygon is unlocated and carries the provider area text`() {
    val event = CapAlertParser.parse(capXml("Kerala", polygon = null), "https://cap-sources.invalid/x.xml")
    assertTrue("official alert must still be surfaced", event != null)
    val geometry = event!!.geometry
    assertTrue(
      "polygon-less alert must be Unlocated, was ${geometry.type}",
      geometry is EventGeometry.Unlocated
    )
    assertEquals("Kerala", (geometry as EventGeometry.Unlocated).areaLabel)
    // No invented coordinates: the old code returned India's centre here.
    assertNull(event.latitude)
    assertNull(event.longitude)
    assertFalse(
      "the alert must not sit on the India centre point",
      event.latitude == IndiaGeo.CENTER_LAT && event.longitude == IndiaGeo.CENTER_LON
    )
    // The official area text is still shown to the user.
    assertEquals("Kerala", event.affectedAreaLabel)
  }

  @Test
  fun `an alert with a real polygon keeps its polygon geometry`() {
    val event = CapAlertParser.parse(
      capXml("Idukki", polygon = "9.5,76.9 9.6,77.0 9.4,77.1"),
      "https://cap-sources.invalid/x.xml"
    )
    assertTrue(event!!.geometry is EventGeometry.Polygon)
    assertEquals(3, (event.geometry as EventGeometry.Polygon).ring.size)
  }

  @Test
  fun `unlocated alerts produce no local hazard zone`() {
    val event = unlocatedEvent()
    assertNull(
      "an alert with no geometry must not become a map hazard zone",
      DisasterEventNormalizer.toHazardZone(event)
    )
  }

  @Test
  fun `an unlocated alert round-trips through the cache without coordinates`() {
    val original = unlocatedEvent()
    val restored = deserializeEvent(serializeEvent(original))
    assertTrue("cache must keep the record", restored != null)
    assertTrue(restored!!.geometry is EventGeometry.Unlocated)
    assertEquals("Kerala", (restored.geometry as EventGeometry.Unlocated).areaLabel)
    assertNull(restored.latitude)
    assertNull(restored.longitude)
  }

  private fun unlocatedEvent() = DisasterEvent(
    id = "imd-cap-test",
    source = DisasterSource.IMD_CAP,
    sourceEventId = "test-1",
    disasterType = DisasterType.HEAVY_RAINFALL,
    title = "Extremely heavy rainfall",
    description = "Official warning text.",
    geometry = EventGeometry.Unlocated("Kerala"),
    latitude = null,
    longitude = null,
    severity = com.example.data.model.HazardSeverity.HIGH,
    observedAtMillis = 1_800_000_000_000L,
    updatedAtMillis = 1_800_000_000_000L,
    origin = EventOrigin.OBSERVED,
    affectedAreaLabel = "Kerala"
  )
}
