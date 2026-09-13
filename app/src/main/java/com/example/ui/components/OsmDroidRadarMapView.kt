package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.preference.PreferenceManager
import com.example.data.PilotRegionData
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import com.example.data.routing.OsrmRoutingService
import com.example.data.routing.RouteResult
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

/**
 * Turn-by-turn live navigation status reported by the map engine.
 */
data class LiveNavStatus(
  val isActive: Boolean = false,
  val nextInstruction: String = "",
  val distanceRemainingMeters: Float = 0f,
  val isOffRoute: Boolean = false,
  val totalDistanceKm: Double = 0.0,
  val totalDurationMins: Int = 0,
  val travelProfileLabel: String = "FOOT EVAC",
  val isArrived: Boolean = false
)

private const val ROUTE_COLOR = 0xFF00E297.toInt()     // High-visibility emergency green
private const val ROUTE_COLOR_DANGER = 0xFFFF1744.toInt()
private const val ROUTE_WIDTH = 10.0f

/**
 * THE single map engine of the application ? OSMDroid + OpenStreetMap with
 * OSRM road routing.
 *
 * Visual language (no tiny dot markers for zones):
 *   HAZARD    = large faded pulsing danger circles (PulsingZoneOverlay)
 *   SAFE ZONE = large faded pulsing safe circles (PulsingZoneOverlay)
 *   USER      = hardware GPS location overlay (dot + accuracy ring)
 *   ROUTE     = OSRM evacuation polyline
 */
@Composable
fun OsmDroidRadarMapView(
  hazardZones: List<HazardZone>,
  safeZones: List<SafeZone>,
  selectedSafeZone: SafeZone?,
  activeRoute: RouteResult?,
  travelMode: String, // "foot" or "driving"
  onRouteComputed: (distanceKm: Double, durationMins: Int, summary: String, isLive: Boolean) -> Unit,
  onSafeZoneSelected: (SafeZone) -> Unit,
  onHazardZoneTapped: (HazardZone) -> Unit,
  onSafeZoneTapped: (SafeZone) -> Unit,
  onRealGpsFix: (latitude: Double, longitude: Double) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val coroutineScope = rememberCoroutineScope()

  var hasLocationPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    hasLocationPermission = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
      (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
  }

  LaunchedEffect(Unit) {
    if (!hasLocationPermission) {
      permissionLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION
        )
      )
    }
  }

  val mapState = remember {
    OsmMapControllerHolder(context) { /* HUD handled by parent screen */ }
  }

  LaunchedEffect(travelMode) { mapState.setTravelMode(travelMode) }

  LaunchedEffect(selectedSafeZone) {
    if (selectedSafeZone != null) mapState.focusOnSafeZone(selectedSafeZone)
  }

  LaunchedEffect(activeRoute) {
    activeRoute?.let { mapState.displayRoute(it) }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> mapState.mapView?.onResume()
        Lifecycle.Event.ON_PAUSE -> mapState.mapView?.onPause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      mapState.cleanup()
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    AndroidView(
      factory = { ctx ->
        mapState.initMapView(
          context = ctx,
          hazardZones = hazardZones,
          safeZones = safeZones,
          onSafeZoneSelected = onSafeZoneSelected,
          onHazardZoneTapped = onHazardZoneTapped,
          onSafeZoneTapped = onSafeZoneTapped,
          onRouteComputed = onRouteComputed,
          onRealGpsFix = onRealGpsFix,
          coroutineScope = coroutineScope
        )
      },
      update = { _ ->
        if (hasLocationPermission) mapState.enableLocationTracking()
      },
      modifier = Modifier.fillMaxSize()
    )

    // ---------------- Floating map controls (right edge, one-hand reachable) --
    Column(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 12.dp, end = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      MapControlButton(Icons.Default.Layers, "Cycle Tile Layer", NeonEmerald, "osmdroid_layer_toggle_button") {
        mapState.cycleTileSource()
      }
      MapControlButton(Icons.Default.Add, "Zoom In", TacticalOnSurface, "osmdroid_zoom_in_button") {
        mapState.zoomIn()
      }
      MapControlButton(Icons.Default.Remove, "Zoom Out", TacticalOnSurface, "osmdroid_zoom_out_button") {
        mapState.zoomOut()
      }
      MapControlButton(Icons.Default.MyLocation, "Recenter My Location", NeonEmerald, "osmdroid_recenter_button") {
        mapState.recenterUser()
      }
      MapControlButton(Icons.Default.DeleteSweep, "Clear Route", EmergencyRed, "osmdroid_clear_route_button") {
        mapState.clearActiveRoute()
        Toast.makeText(context, "Evacuation route cleared", Toast.LENGTH_SHORT).show()
      }
    }

    // ---------------- Region + attribution banner ----------------------------
    Column(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(start = 8.dp, bottom = 8.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(ObsidianContainer.copy(alpha = 0.9f))
        .border(0.5.dp, TacticalOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
        .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
      Text(
        text = "Idukki ? Kerala ? India ? osmdroid / OpenStreetMap / OSRM",
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        color = TacticalOnSurfaceVariant
      )
    }
  }
}

@Composable
private fun MapControlButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  description: String,
  tint: androidx.compose.ui.graphics.Color,
  testTag: String,
  onClick: () -> Unit
) {
  IconButton(
    onClick = onClick,
    modifier = Modifier
      .size(36.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(ObsidianContainer.copy(alpha = 0.95f))
      .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(8.dp))
      .testTag(testTag)
  ) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
  }
}

/**
 * Controller holder managing the map lifecycle, hardware GPS overlay, large
 * pulsing hazard/safe-zone overlays, OSRM route polyline and tap events.
 */
class OsmMapControllerHolder(
  private val appContext: Context,
  private val onLiveNavStatusChanged: (LiveNavStatus) -> Unit
) {

  var mapView: MapView? = null
    private set

  private var locationOverlay: MyLocationNewOverlay? = null
  private var activeRoutingJob: Job? = null
  private var currentTravelMode: String = "foot"
  private var currentRoutePolyline: Polyline? = null

  // Large pulsing zone overlays (hazards + safe zones).
  private val hazardZoneOverlays = mutableListOf<PulsingZoneOverlay>()
  private val safeZoneOverlays = mutableListOf<PulsingZoneOverlay>()

  private var tileSourceIndex = 0
  private val tileSources = listOf(
    TileSourceFactory.MAPNIK,
    TileSourceFactory.OpenTopo
  )

  fun initMapView(
    context: Context,
    hazardZones: List<HazardZone>,
    safeZones: List<SafeZone>,
    onSafeZoneSelected: (SafeZone) -> Unit,
    onHazardZoneTapped: (HazardZone) -> Unit,
    onSafeZoneTapped: (SafeZone) -> Unit,
    onRouteComputed: (distanceKm: Double, durationMins: Int, summary: String, isLive: Boolean) -> Unit,
    onRealGpsFix: (latitude: Double, longitude: Double) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope
  ): MapView {
    // 1. Secure osmdroid configuration + tile cache (offline-first tiles).
    val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    val osmConfig = Configuration.getInstance()
    osmConfig.load(context, sharedPrefs)
    osmConfig.userAgentValue = "${context.packageName}-DisasterRelief/2.0 (Android; OSMDroid)"
    val basePath = File(context.cacheDir, "osmdroid")
    val tilePath = File(basePath, "tiles")
    osmConfig.osmdroidBasePath = basePath
    osmConfig.osmdroidTileCache = tilePath

    // 2. Create MapView ? opens directly on the India pilot region.
    val view = MapView(context).apply {
      setTileSource(tileSources[0])
      setMultiTouchControls(true)
      zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
      controller.setZoom(PilotRegionData.DEFAULT_MAP_ZOOM)
      controller.setCenter(
        OsmGeoPoint(PilotRegionData.DEFAULT_MAP_CENTER.lat, PilotRegionData.DEFAULT_MAP_CENTER.lon)
      )
      setOnTouchListener { v, event ->
        when (event.action) {
          android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
            v.parent?.requestDisallowInterceptTouchEvent(true)
          }
          android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
            v.parent?.requestDisallowInterceptTouchEvent(false)
          }
        }
        false
      }
    }
    mapView = view

    // 3. Hardware GPS location overlay ? REAL fixes reported to the ViewModel.
    val myLoc = object : MyLocationNewOverlay(GpsMyLocationProvider(context), view) {
      override fun onLocationChanged(
        location: android.location.Location?,
        source: org.osmdroid.views.overlay.mylocation.IMyLocationProvider?
      ) {
        super.onLocationChanged(location, source)
        location?.let { fix -> onRealGpsFix(fix.latitude, fix.longitude) }
      }
    }.apply {
      enableMyLocation()
      enableFollowLocation()
      isDrawAccuracyEnabled = true
    }
    locationOverlay = myLoc
    view.overlays.add(myLoc)

    // 4. Deploy LARGE pulsing hazard zones (danger areas).
    deployHazardZones(view, hazardZones, onHazardZoneTapped)

    // 5. Deploy LARGE pulsing safe zones (safe areas).
    deploySafeZones(view, safeZones, onSafeZoneSelected, onSafeZoneTapped)

    return view
  }

  /** Severity-colored large faded danger circles. */
  private fun deployHazardZones(
    view: MapView,
    hazardZones: List<HazardZone>,
    onHazardZoneTapped: (HazardZone) -> Unit
  ) {
    hazardZoneOverlays.forEach { view.overlays.remove(it) }
    hazardZoneOverlays.clear()

    hazardZones.forEach { zone ->
      val color = hazardColor(zone)
      val overlay = PulsingZoneOverlay(
        center = OsmGeoPoint(zone.center.lat, zone.center.lon),
        radiusMeters = zone.radiusMeters,
        baseColorArgb = color,
        pulsePeriodMs = HAZARD_PULSE_MS,
        onZoneTapped = { onHazardZoneTapped(zone) }
      )
      hazardZoneOverlays.add(overlay)
      // Insert UNDER the location overlay so the user dot stays visible.
      view.overlays.add(0, overlay)
    }
    view.invalidate()
  }

  /** Green-toned large faded safe-area circles; full shelters get amber/red. */
  private fun deploySafeZones(
    view: MapView,
    safeZones: List<SafeZone>,
    onSafeZoneSelected: (SafeZone) -> Unit,
    onSafeZoneTapped: (SafeZone) -> Unit
  ) {
    safeZoneOverlays.forEach { view.overlays.remove(it) }
    safeZoneOverlays.clear()

    safeZones.forEach { zone ->
      val color = when {
        zone.capacityStatus == com.example.data.model.CapacityStatus.FULL -> 0xFFF59E0B.toInt() // amber = full
        else -> 0xFF00E297.toInt() // emerald = available
      }
      val overlay = PulsingZoneOverlay(
        center = OsmGeoPoint(zone.lat, zone.lon),
        radiusMeters = SAFE_ZONE_RADIUS_METERS,
        baseColorArgb = color,
        pulsePeriodMs = SAFE_ZONE_PULSE_MS,
        onZoneTapped = {
          onSafeZoneTapped(zone)
          onSafeZoneSelected(zone)
        }
      )
      safeZoneOverlays.add(overlay)
      view.overlays.add(0, overlay)
    }
    view.invalidate()
  }

  private fun hazardColor(zone: HazardZone): Int = when (zone.severity) {
    com.example.data.model.HazardSeverity.EXTREME -> 0xFFFF1744.toInt()
    com.example.data.model.HazardSeverity.HIGH -> 0xFFFF9100.toInt()
    com.example.data.model.HazardSeverity.MODERATE -> 0xFFAB47BC.toInt()
    com.example.data.model.HazardSeverity.LOW -> 0xFFFDD835.toInt()
  }

  // ------------------------------------------------------------ controls

  fun setTravelMode(mode: String) {
    currentTravelMode = if (mode == "driving") "driving" else "foot"
  }

  fun enableLocationTracking() {
    locationOverlay?.let {
      if (!it.isMyLocationEnabled) {
        it.enableMyLocation()
        it.enableFollowLocation()
      }
    }
  }

  fun recenterUser() {
    val myLoc = locationOverlay?.myLocation
    if (myLoc != null) {
      mapView?.controller?.animateTo(myLoc)
    } else {
      // No GPS fix yet ? fall back to the India pilot region center (labeled).
      mapView?.controller?.animateTo(
        OsmGeoPoint(PilotRegionData.FALLBACK_USER_LOCATION.lat, PilotRegionData.FALLBACK_USER_LOCATION.lon)
      )
    }
  }

  fun zoomIn() { mapView?.controller?.zoomIn() }

  fun zoomOut() { mapView?.controller?.zoomOut() }

  fun cycleTileSource() {
    val mv = mapView ?: return
    tileSourceIndex = (tileSourceIndex + 1) % tileSources.size
    mv.setTileSource(tileSources[tileSourceIndex])
    Toast.makeText(appContext, "Map Layer: ${tileSources[tileSourceIndex].name()}", Toast.LENGTH_SHORT).show()
  }

  fun focusOnSafeZone(zone: SafeZone) {
    mapView?.controller?.animateTo(OsmGeoPoint(zone.lat, zone.lon))
  }

  fun clearActiveRoute() {
    activeRoutingJob?.cancel()
    currentRoutePolyline?.let { mapView?.overlays?.remove(it) }
    currentRoutePolyline = null
    mapView?.invalidate()
    onLiveNavStatusChanged(LiveNavStatus(isActive = false))
  }

  /** Renders the current OSRM evacuation polyline (called on route changes). */
  fun displayRoute(route: RouteResult) {
    val mv = mapView ?: return
    currentRoutePolyline?.let { mv.overlays.remove(it) }
    if (route.pathPoints.isEmpty()) return
    val points = route.pathPoints.map { OsmGeoPoint(it.lat, it.lon) }
    val polyline = Polyline(mv).apply {
      setPoints(points)
      outlinePaint.color = if (route.routeSafetyStatus == com.example.data.routing.RouteSafetyStatus.DANGER) ROUTE_COLOR_DANGER else ROUTE_COLOR
      outlinePaint.strokeWidth = ROUTE_WIDTH
      outlinePaint.strokeCap = Paint.Cap.ROUND
      outlinePaint.strokeJoin = Paint.Join.ROUND
    }
    currentRoutePolyline = polyline
    // Insert above zones, below the user location overlay.
    mv.overlays.add(polyline)
    mv.invalidate()
  }

  fun cleanup() {
    activeRoutingJob?.cancel()
    mapView?.onDetach()
  }

  companion object {
    private const val HAZARD_PULSE_MS = 2600L
    private const val SAFE_ZONE_PULSE_MS = 3600L
    private const val SAFE_ZONE_RADIUS_METERS = 900.0
  }
}
