package com.example.data.disaster

/**
 * ============================================================================
 * MAP LAYER REGISTRY — user-togglable layers with zoom-based visibility.
 * ============================================================================
 *
 * The India overview must stay usable: at national zoom only aggregated,
 * high-signal layers render; detail layers appear progressively as the user
 * zooms into state/district/city level. Off by default: heavy point layers.
 */
enum class DisasterLayer(
  val label: String,
  val defaultOn: Boolean,
  /** Below this osmdroid zoom level the layer is hidden (zoom-based LOD). */
  val minZoom: Double
) {
  OFFICIAL_ALERTS("Official Alerts", true, 3.5),
  EARTHQUAKES("Earthquakes", true, 3.5),
  ACTIVE_FIRES("Active Fires", false, 5.0),
  FLOOD_LAYER("Flood Extent (Satellite)", false, 6.5),
  USER_REPORTS("User Reports", true, 8.0),
  SAFE_ZONES("Safe Zones", true, 8.0),
  EVACUATION_ROUTE("Evacuation Route", true, 0.0),
  MY_LOCATION("My GPS Location", true, 0.0);

  fun isVisibleAt(zoomLevel: Double, enabled: Boolean): Boolean =
    enabled && zoomLevel >= minZoom
}

/**
 * Point-cluster generalization for India overview: at low zooms, dense fire
 * detections collapse into aggregated cells so the national view stays
 * readable. Pure geometry — unit-testable.
 */
object MarkerGeneralizer {

  /**
   * Grid-cluster [events] at [zoomLevel]: events within one grid cell merge
   * into a single aggregate marker (the most severe event represents the
   * cell). At high zooms (>= [DETAIL_ZOOM]) nothing is clustered.
   */
  fun generalize(events: List<DisasterEvent>, zoomLevel: Double): List<DisasterEvent> {
    if (zoomLevel >= DETAIL_ZOOM || events.size < 2) return events
    val cellSize = cellDegrees(zoomLevel)
    val grid = LinkedHashMap<String, DisasterEvent>()
    for (event in events) {
      val point = when (val g = event.geometry) {
        is EventGeometry.Point -> g
        else -> continue // polygons/lines are rendered directly, not clustered
      }
      val key = "${Math.floor(point.lat / cellSize)}:${Math.floor(point.lon / cellSize)}"
      val existing = grid[key]
      if (existing == null || event.severity.weight > existing.severity.weight) {
        grid[key] = event
      }
    }
    return grid.values.toList()
  }

  fun cellDegrees(zoomLevel: Double): Double = when {
    zoomLevel < 4.0 -> 2.0
    zoomLevel < 5.0 -> 1.0
    zoomLevel < 6.0 -> 0.5
    zoomLevel < 7.0 -> 0.25
    else -> 0.1
  }

  const val DETAIL_ZOOM = 8.0
}
