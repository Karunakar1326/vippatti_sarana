package com.example.data.disaster

import com.example.data.model.HazardSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIRE LAYER - intensity from the provider's own FRP.
 *
 * Rules under test:
 *  - inside one grid cell the STRONGEST real detection represents the cluster;
 *  - the cluster reports how many detections it stands for and the max FRP;
 *  - marker size grows with intensity and cluster size, always bounded;
 *  - fire severity is derived from FRP + confidence, never a fixed value;
 *  - a detection without an FRP measurement is never escalated or invented.
 */
class FireLayerIntensityTest {

  private val zoomNational = 4.5

  private fun fire(
    id: String,
    lat: Double,
    lon: Double,
    frp: Double?,
    confidence: EventConfidence = EventConfidence.NOMINAL,
    observedAt: Long = 1_800_000_000_000L
  ) = DisasterEvent(
    id = id,
    source = DisasterSource.NASA_FIRMS,
    sourceEventId = id,
    disasterType = DisasterType.WILDFIRE,
    title = "Active Fire Detection",
    description = "",
    geometry = EventGeometry.Point(lat, lon),
    latitude = lat,
    longitude = lon,
    severity = FireIntensityScale.severityFor(frp, confidence),
    confidence = confidence,
    observedAtMillis = observedAt,
    updatedAtMillis = observedAt,
    origin = EventOrigin.OBSERVED,
    details = EventDetails.Fire("N", "VIIRS", frp, "D")
  )

  // ----------------------------------------------------------- clustering

  @Test
  fun `the strongest detection in a cell represents the cluster`() {
    val events = listOf(
      fire("weak", 20.10, 85.10, frp = 1.0),
      fire("strongest", 20.12, 85.12, frp = 48.0),
      fire("middle", 20.14, 85.14, frp = 12.0)
    )
    val clusters = MarkerGeneralizer.clusters(events, zoomNational)
    assertEquals(1, clusters.size)
    assertEquals("strongest", clusters[0].representative.id)
    assertEquals(3, clusters[0].memberCount)
    assertEquals(48.0, clusters[0].maxFrpMegawatts!!, 0.001)
  }

  @Test
  fun `a detection without FRP never outranks a measured one`() {
    val events = listOf(
      fire("unmeasured", 20.10, 85.10, frp = null),
      fire("measured", 20.11, 85.11, frp = 6.0)
    )
    val clusters = MarkerGeneralizer.clusters(events, zoomNational)
    assertEquals("measured", clusters[0].representative.id)
    assertEquals(6.0, clusters[0].maxFrpMegawatts!!, 0.001)
  }

  @Test
  fun `distant detections stay separate markers`() {
    val events = listOf(
      fire("odisha", 20.10, 85.10, frp = 30.0),
      fire("jharkhand", 23.60, 85.90, frp = 30.0)
    )
    assertEquals(2, MarkerGeneralizer.clusters(events, zoomNational).size)
  }

  @Test
  fun `at detail zoom nothing is clustered`() {
    val events = listOf(
      fire("a", 20.10, 85.10, frp = 30.0),
      fire("b", 20.11, 85.11, frp = 5.0)
    )
    val clusters = MarkerGeneralizer.clusters(events, MarkerGeneralizer.DETAIL_ZOOM)
    assertEquals(2, clusters.size)
    assertTrue(clusters.all { it.memberCount == 1 })
  }

  @Test
  fun `non-fire events still rank by hazard severity`() {
    // A severe quake and a weak fire in the same cell: the severe hazard
    // represents the marker even though the fire has an FRP measurement.
    val quake = fire("quake", 20.10, 85.10, frp = null).copy(
      disasterType = DisasterType.EARTHQUAKE,
      severity = HazardSeverity.EXTREME,
      details = EventDetails.Generic
    )
    val smallFire = fire("small", 20.11, 85.11, frp = 3.0)
    val clusters = MarkerGeneralizer.clusters(listOf(smallFire, quake), zoomNational)
    assertEquals("quake", clusters[0].representative.id)
    assertEquals(2, clusters[0].memberCount)
    // The strongest MEASURED FRP among the merged detections is still reported
    // (the quake carries none, so only the fire contributes).
    assertEquals(3.0, clusters[0].maxFrpMegawatts!!, 0.001)
    assertNull(FireIntensityScale.frpOf(quake))
  }

  @Test
  fun `a single detection is its own cluster with its own FRP`() {
    val clusters = MarkerGeneralizer.clusters(
      listOf(fire("only", 9.85, 76.96, frp = 60.0)),
      zoomNational
    )
    assertEquals(1, clusters.size)
    assertEquals(1, clusters[0].memberCount)
    assertEquals(60.0, clusters[0].maxFrpMegawatts!!, 0.001)
  }

  // -------------------------------------------------------- marker scaling

  @Test
  fun `marker size grows with intensity and cluster size, always bounded`() {
    assertEquals(1.0, FireIntensityScale.markerScale(1.0, 1), 0.0001)
    assertTrue(FireIntensityScale.markerScale(48.0, 1) > FireIntensityScale.markerScale(1.0, 1))
    assertTrue(FireIntensityScale.markerScale(48.0, 250) > FireIntensityScale.markerScale(48.0, 1))
    assertTrue(
      "cluster growth must stay bounded",
      FireIntensityScale.markerScale(500.0, 10_000) <= FireIntensityScale.MAX_MARKER_SCALE
    )
    assertTrue(FireIntensityScale.markerScale(null, 1) == 1.0)
  }

  // ------------------------------------------------------ severity mapping

  @Test
  fun `fire severity comes from FRP, not a fixed value`() {
    assertEquals(HazardSeverity.LOW, FireIntensityScale.severityFor(1.0, EventConfidence.NOMINAL))
    assertEquals(
      HazardSeverity.MODERATE,
      FireIntensityScale.severityFor(8.0, EventConfidence.NOMINAL)
    )
    assertEquals(HazardSeverity.HIGH, FireIntensityScale.severityFor(40.0, EventConfidence.NOMINAL))
    assertEquals(
      HazardSeverity.EXTREME,
      FireIntensityScale.severityFor(250.0, EventConfidence.NOMINAL)
    )
  }

  @Test
  fun `high confidence escalates by one step and unknown FRP is never escalated`() {
    assertEquals(HazardSeverity.MODERATE, FireIntensityScale.severityFor(1.0, EventConfidence.HIGH))
    // No measurement -> conservative MODERATE, and confidence cannot escalate it.
    assertEquals(HazardSeverity.MODERATE, FireIntensityScale.severityFor(null, EventConfidence.HIGH))
    assertEquals(FireIntensity.UNKNOWN, FireIntensityScale.of(null as Double?))
  }
}
