package com.example.data.routing

import com.example.data.routing.GeoPoint
import com.example.data.routing.OsrmRoutingService
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OSRM road-routing contracts: the request must carry coordinates as
 * longitude,latitude with full GeoJSON geometry, and the decoder must map
 * EVERY returned [lon, lat] pair to GeoPoint(lat, lon) in order — never a
 * straight origin-to-destination collapse.
 */
class OsrmRequestTest {

  // Bangalore -> Mysuru (the spec's example shape, several km apart).
  private val origin = GeoPoint(12.9716, 77.5946)
  private val destination = GeoPoint(12.2958, 76.6394)

  @Test
  fun `url carries longitude before latitude`() {
    val url = OsrmRoutingService.buildRouteUrl(
      "https://routing.openstreetmap.de/routed-car/route/v1/driving",
      origin,
      destination,
      1
    )
    assertTrue("url=$url", url.contains("77.5946,12.9716;76.6394,12.2958"))
  }

  @Test
  fun `url requests full geojson geometry with steps`() {
    val url = OsrmRoutingService.buildRouteUrl("https://e/route/v1/driving", origin, destination, 3)
    assertTrue("url=$url", url.contains("overview=full"))
    assertTrue("url=$url", url.contains("geometries=geojson"))
    assertTrue("url=$url", url.contains("steps=true"))
    assertTrue("url=$url", url.contains("alternatives=3"))
    assertTrue("url=$url", url.startsWith("https://e/route/v1/driving/"))
  }

  @Test
  fun `retry url lowers overview to simplified but keeps road geometry`() {
    val url = OsrmRoutingService.buildRouteUrl(
      "https://routing.openstreetmap.de/routed-car/route/v1/driving",
      origin,
      destination,
      1,
      overview = "simplified"
    )
    assertTrue("url=$url", url.contains("overview=simplified"))
    assertTrue("url=$url", url.contains("geometries=geojson"))
    assertTrue("url=$url", url.contains("77.5946,12.9716;76.6394,12.2958"))
  }

  @Test
  fun `decoder maps lon-lat pairs to lat-lon points in order`() {
    val coords = JSONArray(
      "[[77.5946,12.9716],[77.58,12.96],[76.6394,12.2958]]"
    )
    val points = OsrmRoutingService.decodeGeoJsonCoordinates(coords)
    assertEquals(3, points.size)
    assertEquals(12.9716, points[0].lat, 1e-9)
    assertEquals(77.5946, points[0].lon, 1e-9)
    assertEquals(12.96, points[1].lat, 1e-9)
    assertEquals(77.58, points[1].lon, 1e-9)
    assertEquals(12.2958, points[2].lat, 1e-9)
    assertEquals(76.6394, points[2].lon, 1e-9)
  }

  @Test
  fun `decoder keeps intermediate bends instead of collapsing`() {
    // A route that visibly turns: intermediate points must survive decoding
    // or the Polyline would cut straight across the map.
    val coords = JSONArray(
      "[[77.0,13.0],[77.1,13.0],[77.1,12.9],[77.2,12.9],[77.2,12.8]]"
    )
    val points = OsrmRoutingService.decodeGeoJsonCoordinates(coords)
    assertEquals(5, points.size)
    assertEquals(GeoPoint(13.0, 77.1), points[1])
    assertEquals(GeoPoint(12.9, 77.1), points[2])
  }

  @Test
  fun `decoder handles an empty geometry without crashing`() {
    assertTrue(OsrmRoutingService.decodeGeoJsonCoordinates(JSONArray("[]")).isEmpty())
  }
}
