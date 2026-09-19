package com.example.data.disaster

import com.example.data.disaster.PilotRegionData
import com.example.data.disaster.IndiaGeo
import com.example.data.model.GeoMath
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Radar-map mock network contracts:
 *  - 12-state India-only mock, one district per zone, small separated zones;
 *  - several distinct disaster types across regions;
 *  - every danger zone paired with a nearby but genuinely-safe shelter;
 *  - shortest-distance helper resolves a nearest safe zone;
 *  - empty-list helpers stay null (mock-OFF empty map).
 */
class IndiaMockNetworkTest {

  @Test
  fun `all mock coordinates lie inside India`() {
    assertTrue(PilotRegionData.allCoordinatesInIndia())
    PilotRegionData.hazardZones.forEach { hz ->
      assertTrue("hazard ${hz.id} outside India", IndiaGeo.contains(hz.center))
    }
    PilotRegionData.safeZones.forEach { sz ->
      assertTrue("shelter ${sz.id} outside India", IndiaGeo.contains(sz.point))
    }
  }

  @Test
  fun `mock spans fourteen districts with one zone pair each`() {
    assertEquals(14, PilotRegionData.hazardZones.size)
    assertEquals(14, PilotRegionData.safeZones.size)
    assertEquals(
      28,
      (PilotRegionData.hazardZones.map { it.id } +
        PilotRegionData.safeZones.map { it.id }).toSet().size
    )
  }

  @Test
  fun `mock covers several distinct disaster types across states`() {
    val types = PilotRegionData.hazardZones.map { it.type }.toSet()
    assertTrue("need >= 7 disaster types, got $types", types.size >= 7)
    val labels = types.map { it.label }
    assertTrue(labels.any { it.contains("Flood", ignoreCase = true) })
    assertTrue(labels.any { it.contains("Landslide", ignoreCase = true) })
    assertTrue(labels.any { it.contains("Earthquake", ignoreCase = true) })
    assertTrue(labels.any { it.contains("Cyclone", ignoreCase = true) })
    assertTrue(labels.any { it.contains("Fire", ignoreCase = true) })
    assertTrue(labels.any { it.contains("Heavy Rainfall", ignoreCase = true) })
    assertTrue(labels.any { it.contains("Weather Alert", ignoreCase = true) })
  }

  @Test
  fun `danger zones are small and widely separated`() {
    // Compact radii: nothing larger than 2.5 km.
    PilotRegionData.hazardZones.forEach { hz ->
      assertTrue(
        "danger ${hz.id} too large: ${hz.radiusMeters}",
        hz.radiusMeters <= 2_500.0
      )
    }
    // Closest danger pair is ~185 km apart — never on the same tile.
    assertTrue(PilotRegionData.allZonesSeparatedAndSafe(minSeparationMeters = 50_000.0))
  }

  @Test
  fun `every danger zone has a nearby but genuinely safe shelter`() {
    assertEquals(PilotRegionData.hazardZones.size, PilotRegionData.safeZones.size)
    PilotRegionData.hazardZones.indices.forEach { i ->
      val hz = PilotRegionData.hazardZones[i]
      val sz = PilotRegionData.safeZones[i]
      val d = GeoMath.distanceMeters(hz.center, sz.point)
      // Outside the danger radius with real margin ...
      assertTrue(
        "shelter ${sz.id} inside danger ${hz.id} (d=$d r=${hz.radiusMeters})",
        d > hz.radiusMeters
      )
      // ... yet nearby (same district, under 12 km).
      assertTrue("shelter ${sz.id} too far from ${hz.id} (d=$d)", d < 12_000.0)
    }
  }

  @Test
  fun `every disaster zone at least one safe zone is reachable nearby`() {
    // Network-level guarantee (issue #2): no matter which disaster zone the
    // user stands in, at least one shelter exists within a short drive. The
    // paired-zone test covers the primary shelter; this proves the whole set
    // never leaves a zone without ANY nearby destination.
    PilotRegionData.hazardZones.forEach { hz ->
      val nearest = PilotRegionData.safeZones
        .minByOrNull { GeoMath.distanceMeters(hz.center, it.point) }
      assertNotNull("no safe zone reachable from ${hz.id}", nearest)
      val d = GeoMath.distanceMeters(hz.center, nearest!!.point)
      assertTrue(
        "nearest shelter to ${hz.id} is ${d.toInt()} m away — too far",
        d <= 15_000.0
      )
    }
  }

  @Test
  fun `shortest distance resolves nearest safe zone per region`() {
    // User standing at the Assam flood pocket -> nearest shelter is Assam hall.
    val assamUser = GeoPoint(27.4728, 94.9120)
    val nearest = PilotRegionData.nearestSafeZone(assamUser)
    assertNotNull(nearest)
    assertEquals("sz-assam-dibrugarh-hall", nearest!!.id)
    val shortest = PilotRegionData.shortestDistanceMeters(assamUser)
    assertNotNull(shortest)
    // ~8.6 km to the genuinely-safe hall (no shelter sits inside danger).
    assertTrue("shortest=$shortest", shortest!! in 5_000.0..12_000.0)
  }

  @Test
  fun `shortest distance is null when mock list is empty (toggle OFF)`() {
    assertNull(PilotRegionData.shortestDistanceMeters(GeoPoint(20.0, 78.0), emptyList()))
    assertNull(PilotRegionData.nearestSafeZone(GeoPoint(20.0, 78.0), emptyList()))
  }

  @Test
  fun `standing at a shelter entrance yields near-zero distance`() {
    val hall = PilotRegionData.safeZones.first { it.id == "sz-assam-dibrugarh-hall" }
    val d = PilotRegionData.shortestDistanceMeters(hall.point)
    assertNotNull(d)
    assertTrue(d!! < 1.0)
  }
}
