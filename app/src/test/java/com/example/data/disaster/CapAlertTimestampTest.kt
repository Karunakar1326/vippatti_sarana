package com.example.data.disaster

import com.example.data.disaster.providers.CapAlertParser
import com.example.data.model.HazardSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * STAGE 3 — CAP timestamp-chain contracts.
 *
 * Pins the parser's observation-time priority (`sent` → `onset` →
 * `effective`, all real provider timestamps) and documents the last-resort
 * behaviour: an alert with none of these is still surfaced (never silently
 * dropped) with the retrieval time. No existing validation is weakened —
 * malformed alerts (no identifier/event) are still dropped, expiry still
 * drives EXPIRED/ACTIVE, and polygon-less alerts stay Unlocated.
 */
@RunWith(RobolectricTestRunner::class)
class CapAlertTimestampTest {

  private fun capXml(
    identifier: String? = "IMD-TEST-TS",
    event: String? = "Extremely heavy rainfall",
    sent: String? = "2026-09-20T06:00:00+05:30",
    onset: String? = null,
    effective: String? = null,
    expires: String? = "2026-09-21T06:00:00+05:30",
    severity: String? = "Severe",
    areaDesc: String = "Kerala",
    polygon: String? = null
  ): String {
    val sentTag = sent?.let { "<sent>$it</sent>" } ?: ""
    val onsetTag = onset?.let { "<onset>$it</onset>" } ?: ""
    val effectiveTag = effective?.let { "<effective>$it</effective>" } ?: ""
    val expiresTag = expires?.let { "<expires>$it</expires>" } ?: ""
    val severityTag = severity?.let { "<severity>$it</severity>" } ?: ""
    val identifierTag = identifier?.let { "<identifier>$it</identifier>" } ?: ""
    val eventTag = event?.let { "<event>$it</event>" } ?: ""
    return """
    <?xml version="1.0" encoding="UTF-8"?>
    <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
      $identifierTag
      <sender>imd@test</sender>
      $sentTag
      <status>Actual</status>
      <msgType>Alert</msgType>
      <info>
        <category>Met</category>
        $eventTag
        <urgency>Immediate</urgency>
        $severityTag
        <certainty>Likely</certainty>
        $onsetTag
        $effectiveTag
        $expiresTag
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
  }

  @Test
  fun `sent is the primary observed time`() {
    val sent = "2026-09-20T06:00:00+05:30"
    val event = CapAlertParser.parse(capXml(sent = sent), "https://cap-sources.invalid/x.xml")
    assertTrue(event != null)
    assertEquals(CapAlertParser.parseCapTimestamp(sent), event!!.observedAtMillis)
    assertEquals(event.observedAtMillis, event.updatedAtMillis)
  }

  @Test
  fun `a missing sent falls back to the alert onset, not the current time`() {
    val onset = "2026-09-20T04:00:00+05:30"
    val event = CapAlertParser.parse(
      capXml(sent = null, onset = onset),
      "https://cap-sources.invalid/x.xml"
    )
    assertTrue(event != null)
    assertEquals(CapAlertParser.parseCapTimestamp(onset), event!!.observedAtMillis)
  }

  @Test
  fun `missing sent and onset fall back to effective`() {
    val effective = "2026-09-20T03:00:00+05:30"
    val event = CapAlertParser.parse(
      capXml(sent = null, onset = null, effective = effective),
      "https://cap-sources.invalid/x.xml"
    )
    assertTrue(event != null)
    assertEquals(CapAlertParser.parseCapTimestamp(effective), event!!.observedAtMillis)
  }

  @Test
  fun `an alert with no publication timestamp is still surfaced, never dropped`() {
    val before = System.currentTimeMillis()
    val event = CapAlertParser.parse(
      capXml(sent = null, onset = null, effective = null),
      "https://cap-sources.invalid/x.xml"
    )
    val after = System.currentTimeMillis()
    assertTrue("official alert without timestamps must still be surfaced", event != null)
    // Last-resort retrieval time: within the parse window, never zero.
    assertTrue(event!!.observedAtMillis in before..after)
    // No invented coordinates and no invented area text.
    assertTrue(event.geometry is EventGeometry.Unlocated)
    assertEquals("Kerala", (event.geometry as EventGeometry.Unlocated).areaLabel)
    assertEquals("Kerala", event.affectedAreaLabel)
  }

  @Test
  fun `a malformed alert without identifier or event is dropped`() {
    assertNull(
      CapAlertParser.parse(capXml(identifier = null), "https://cap-sources.invalid/x.xml")
    )
    assertNull(
      CapAlertParser.parse(capXml(event = null), "https://cap-sources.invalid/x.xml")
    )
  }

  @Test
  fun `a past expiry marks the alert expired, a missing expiry stays active`() {
    val expired = CapAlertParser.parse(
      capXml(expires = "2020-01-01T00:00:00+05:30"),
      "https://cap-sources.invalid/x.xml"
    )
    assertEquals(EventStatus.EXPIRED, expired!!.status)

    val active = CapAlertParser.parse(
      capXml(expires = null),
      "https://cap-sources.invalid/x.xml"
    )
    assertEquals(EventStatus.ACTIVE, active!!.status)
    assertNull(active.expiresAtMillis)
  }

  @Test
  fun `severity mapping keeps the pre-existing scale including the unknown default`() {
    assertEquals(HazardSeverity.EXTREME, CapAlertParser.mapSeverity("Extreme"))
    assertEquals(HazardSeverity.HIGH, CapAlertParser.mapSeverity("Severe"))
    assertEquals(HazardSeverity.MODERATE, CapAlertParser.mapSeverity("Moderate"))
    assertEquals(HazardSeverity.LOW, CapAlertParser.mapSeverity("Minor"))
    assertEquals(HazardSeverity.MODERATE, CapAlertParser.mapSeverity("Unknown"))
    assertEquals(HazardSeverity.MODERATE, CapAlertParser.mapSeverity(null))
    assertEquals(HazardSeverity.MODERATE, CapAlertParser.mapSeverity(""))
  }
}
