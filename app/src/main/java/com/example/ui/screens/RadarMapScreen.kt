package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WeatherMetrics
import com.example.data.model.SafeZone
import com.example.data.routing.OsrmRoutingService
import com.example.data.routing.RouteSafetyStatus
import com.example.data.risk.RiskLevel
import com.example.data.shelters.SafeZoneEvaluation
import com.example.data.shelters.SafeZoneEvaluator
import com.example.ui.components.OsmDroidRadarMapView
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldContainer
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnEmergencyRedContainer
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VippattiUiState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * RADAR MAP — mobile-first map decision screen.
 *
 * Info hierarchy (tactical identity preserved, responsive layout):
 *  1. FULL-BLEED MAP — large pulsing hazard/safe-zone circles, OSRM route
 *     polyline. The map stays fully draggable/zoomable under the sheet.
 *  2. Floating PERSONAL RISK strip (RED/ORANGE/YELLOW/GREEN + explanation)
 *     and the SOS broadcast button.
 *  3. COLLAPSIBLE BOTTOM SHEET:
 *       COLLAPSED -> drag handle + current destination one-liner (compact)
 *       EXPANDED  -> WHAT SHOULD I DO? -> horizontal safe-zone carousel ->
 *                   compact weather row -> route intelligence + navigation.
 *  4. Live turn-by-turn HUD pinned under the risk strip while guidance runs,
 *     always tied to the SELECTED destination.
 */
@Composable
fun RadarMapScreen(
  uiState: VippattiUiState,
  onSelectBestSafeZone: () -> Unit,
  onSelectSafeZone: (SafeZone) -> Unit,
  onSetTravelMode: (String) -> Unit,
  onStartEvacuation: () -> Unit,
  onStopEvacuation: () -> Unit,
  onNextNavigationStep: () -> Unit,
  onLoadAlternativeRoutes: () -> Unit,
  onOpenSensorBroadcast: () -> Unit,
  onClearRoute: () -> Unit,
  onRealGpsFix: (latitude: Double, longitude: Double) -> Unit,
  onOpenHazardDetail: (com.example.data.model.HazardZone) -> Unit,
  onOpenSafeZoneDetail: (SafeZone) -> Unit,
  modifier: Modifier = Modifier
) {
  var isSheetExpanded by remember { mutableStateOf(true) }
  val sheetPeekHeight = 88.dp

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianSurface)
  ) {
    // Responsive sheet sizing — 68% of the available height on tall screens,
    // but never larger than the screen (short phones / landscape) and never
    // smaller than a usable peek of the expanded content.
    val sheetExpandedHeight = (maxHeight * 0.68f).coerceIn(320.dp, maxHeight * 0.92f)
    val animatedSheetHeight by animateDpAsState(
      targetValue = if (isSheetExpanded) sheetExpandedHeight else sheetPeekHeight,
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
      ),
      label = "radar_sheet_height"
    )

    // 1. FULL-BLEED MAP — the single OSMDroid engine (draggable, zoomable).
    //    The floating overlay heights are MEASURED (not assumed) so the map's
    //    control stack and attribution banner reposition themselves cleanly
    //    on every screen size, density and font scale.
    val density = LocalDensity.current
    var topOverlayHeightPx by remember { mutableStateOf(0) }
    val topOverlayPadding = (topOverlayHeightPx / density.density).dp

    OsmDroidRadarMapView(
      hazardZones = uiState.hazardZones,
      safeZones = uiState.safeZones,
      selectedSafeZone = uiState.selectedSafeZone,
      activeRoute = uiState.activeRoute,
      travelMode = uiState.travelMode,
      onClearRoute = onClearRoute,
      onSafeZoneSelected = onSelectSafeZone,
      onHazardZoneTapped = onOpenHazardDetail,
      onSafeZoneTapped = onOpenSafeZoneDetail,
      onRealGpsFix = onRealGpsFix,
      modifier = Modifier.fillMaxSize(),
      topOverlayPadding = topOverlayPadding + 8.dp,
      bottomOverlayPadding = animatedSheetHeight
    )

    // 2. Floating top strip: personal risk + SOS broadcast.
    //    Structured as a Column flow (not absolute offsets) so the map's
    //    right-edge control stack always starts BELOW the strip — no overlap
    //    at any width/density, and the strip wraps instead of being clipped.
    Column(
      modifier = Modifier
        .align(Alignment.TopStart)
        .fillMaxWidth()
        .onSizeChanged { size -> topOverlayHeightPx = size.height }
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        PersonalRiskStrip(
          risk = uiState.personalRisk,
          isFallbackLocation = uiState.isUserLocationFallback,
          modifier = Modifier.weight(1f)
        )
        IconButton(
          onClick = onOpenSensorBroadcast,
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(EmergencyRed.copy(alpha = 0.9f))
            .testTag("mesh_sensor_broadcast_button")
        ) {
          Icon(
            imageVector = Icons.Default.Sensors,
            contentDescription = "Emergency SOS Broadcast",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      // 3. Live turn-by-turn HUD — directly under the risk strip while
      //    guidance is active, always following the SELECTED destination's
      //    route. Flows below the strip (no hardcoded top offset).
      AnimatedVisibility(visible = uiState.isNavigatingLive) {
        LiveNavigationHud(
          uiState = uiState,
          onNextNavigationStep = onNextNavigationStep,
          onStopEvacuation = onStopEvacuation
        )
      }
    }

    // 4. COLLAPSIBLE BOTTOM SHEET — tap handle or flick to collapse/expand;
    //    the map underneath stays fully interactive. The drag gesture lives
    //    ONLY on the grabber strip so the scrollable expanded content keeps
    //    normal vertical scrolling.
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(animatedSheetHeight)
        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        .background(ObsidianContainerLowest.copy(alpha = 0.98f))
        .border(
          1.dp,
          TacticalOutlineVariant.copy(alpha = 0.5f),
          RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        )
    ) {
      // Grabber strip — tap toggles; flick up/down expands/collapses.
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(28.dp)
          .clickable { isSheetExpanded = !isSheetExpanded }
          .pointerInput(Unit) {
            detectVerticalDragGestures(
              onVerticalDrag = { _, dragAmount ->
                // Flick DOWN to collapse / UP to expand — dead-zone avoids
                // accidental state flips from micro-drags.
                if (abs(dragAmount) > 2f) isSheetExpanded = dragAmount < 0
              }
            )
          }
          .testTag("radar_sheet_handle"),
        contentAlignment = Alignment.Center
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(
            modifier = Modifier
              .width(42.dp)
              .height(4.dp)
              .clip(CircleShape)
              .background(TacticalOutlineVariant.copy(alpha = 0.8f))
          )
          Icon(
            imageVector = if (isSheetExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = if (isSheetExpanded) "Collapse panel" else "Expand panel",
            tint = TacticalOnSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      // COLLAPSED payload: handle + current destination one-liner (compact).
      if (!isSheetExpanded) {
        CollapsedSheetContent(uiState = uiState)
      }

      // EXPANDED payload: What-should-I-do -> carousel -> weather -> route.
      if (isSheetExpanded) {
        ExpandedSheetContent(
          uiState = uiState,
          onSelectBestSafeZone = onSelectBestSafeZone,
          onSelectSafeZone = onSelectSafeZone,
          onSetTravelMode = onSetTravelMode,
          onStartEvacuation = onStartEvacuation,
          onStopEvacuation = onStopEvacuation,
          onLoadAlternativeRoutes = onLoadAlternativeRoutes,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

// ============================================================================
// SHEET PAYLOADS
// ============================================================================

/** COLLAPSED state: compact destination status line under the handle. */
@Composable
private fun CollapsedSheetContent(uiState: VippattiUiState) {
  val route = uiState.activeRoute
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Icon(
      imageVector = Icons.Default.Place,
      contentDescription = null,
      tint = NeonEmerald,
      modifier = Modifier.size(18.dp)
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "DESTINATION",
        fontSize = 8.sp,
        fontWeight = FontWeight.Black,
        color = TacticalOnSurfaceVariant,
        letterSpacing = 0.8.sp
      )
      Text(
        text = uiState.selectedSafeZone?.name ?: "No safe zone selected — expand to choose",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TacticalOnSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    if (uiState.isCalculatingRoute) {
      CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        strokeWidth = 1.5.dp,
        color = NeonEmerald
      )
    } else {
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = route?.let { OsrmRoutingService.formatDistance(it.distanceMeters) } ?: "--",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = NeonEmerald,
          maxLines = 1
        )
        Text(
          text = route?.let { OsrmRoutingService.formatDuration(it.durationSeconds) } ?: "no route",
          fontSize = 9.sp,
          color = TacticalOnSurfaceVariant,
          maxLines = 1
        )
      }
    }
  }
}

/** EXPANDED state: full decision stack in the required priority order. */
@Composable
private fun ExpandedSheetContent(
  uiState: VippattiUiState,
  onSelectBestSafeZone: () -> Unit,
  onSelectSafeZone: (SafeZone) -> Unit,
  onSetTravelMode: (String) -> Unit,
  onStartEvacuation: () -> Unit,
  onStopEvacuation: () -> Unit,
  onLoadAlternativeRoutes: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(bottom = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // 1. WHAT SHOULD I DO?
    RecommendedActionCard(
      action = uiState.recommendedAction,
      selectedZoneName = uiState.selectedSafeZone?.name,
      onWhyThisZone = onSelectBestSafeZone,
      isCalculating = uiState.isCalculatingRoute
    )

    // 2. HORIZONTAL SAFE-ZONE CAROUSEL (swipe, next card peeks).
    SafeZoneCarousel(
      uiState = uiState,
      onSelectSafeZone = onSelectSafeZone
    )

    // 3. COMPACT WEATHER ROW.
    CompactWeatherRow(weather = uiState.weather)

    // 4. ROUTE INTELLIGENCE + NAVIGATION CONTROLS.
    RouteIntelligencePanel(
      uiState = uiState,
      onSetTravelMode = onSetTravelMode,
      onLoadAlternativeRoutes = onLoadAlternativeRoutes,
      onSelectBestSafeZone = onSelectBestSafeZone
    )

    // 5. Large one-hand evacuation CTA.
    EvacuationCta(
      uiState = uiState,
      onStartEvacuation = onStartEvacuation,
      onStopEvacuation = onStopEvacuation
    )
  }
}

// ============================================================================
// HORIZONTAL SAFE-ZONE CAROUSEL — swipe; ~2 cards visible, next one peeks
// ============================================================================

/**
 * Horizontal card carousel of ALL pilot safe zones. Tapping a card SELECTS
 * that shelter as the evacuation destination — the ViewModel re-runs the
 * OSRM route to it and every downstream metric (distance, ETA, capacity,
 * route safety, hazard warnings, guidance) follows the selection.
 */
@Composable
private fun SafeZoneCarousel(
  uiState: VippattiUiState,
  onSelectSafeZone: (SafeZone) -> Unit
) {
  val listState = rememberLazyListState()
  val selectedId = uiState.selectedSafeZone?.id

  // Keep the selected card visible in the carousel when selection changes
  // (map circle tap, "Best Zone" button, or initial recommendation).
  val selectedIndex = uiState.safeZones.indexOfFirst { it.id == selectedId }
  LaunchedEffect(selectedId) {
    if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "SAFE ZONES — TAP TO ROUTE",
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        color = TacticalOnSurfaceVariant,
        letterSpacing = 0.6.sp
      )
      Icon(
        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
        contentDescription = null,
        tint = TacticalOnSurfaceVariant,
        modifier = Modifier.size(12.dp)
      )
    }

    LazyRow(
      state = listState,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("safe_zone_carousel"),
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(uiState.safeZones, key = { it.id }) { zone ->
        val isSelected = zone.id == selectedId
        SafeZoneCard(
          zone = zone,
          evaluation = uiState.rankedShelters.firstOrNull { it.zone.id == zone.id },
          isSelected = isSelected,
          isCalculatingRoute = uiState.isCalculatingRoute && isSelected,
          onSelect = { onSelectSafeZone(zone) },
          // Responsive carousel card: ~72% of the viewport width so the next
          // card always peeks, clamped to a sensible max on tablets.
          modifier = Modifier
            .fillParentMaxWidth(0.72f)
            .widthIn(max = 280.dp)
        )
      }
    }
  }
}

/**
 * One safe-zone card: name, safety badge, distance/ETA, capacity bar with
 * available/total, occupancy %, facilities and a route action.
 */
@Composable
private fun SafeZoneCard(
  zone: SafeZone,
  evaluation: SafeZoneEvaluation?,
  isSelected: Boolean,
  isCalculatingRoute: Boolean,
  onSelect: () -> Unit,
  modifier: Modifier = Modifier
) {
  val capacity = evaluation?.capacityReport
  val occupancyRatio = if (zone.capacityTotal > 0) {
    (zone.capacityCurrent.toFloat() / zone.capacityTotal).coerceIn(0f, 1f)
  } else 1f
  val isFull = capacity?.acceptsNewOccupants == false || zone.availableCapacity <= 0
  val distanceKm = evaluation?.distanceMeters ?: 0.0
  val etaMins = SafeZoneEvaluator.estimateTravelMinutes(distanceKm, 1.35)

  Column(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(if (isSelected) ObsidianContainerHigh else ObsidianContainer)
      .border(
        1.5.dp,
        when {
          isSelected -> NeonEmerald
          isFull -> EmergencyRed.copy(alpha = 0.5f)
          evaluation == null -> TacticalOutlineVariant.copy(alpha = 0.4f)
          else -> TacticalOutlineVariant.copy(alpha = 0.4f)
        },
        RoundedCornerShape(14.dp)
      )
      .clickable(onClick = onSelect)
      .padding(10.dp)
      .testTag("safe_zone_card_${zone.id}"),
    verticalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    // Card header: name + selected check.
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      Text(
        text = zone.name,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        color = TacticalOnSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 14.sp,
        modifier = Modifier.weight(1f, fill = false)
      )
      if (isSelected) {
        Icon(
          imageVector = if (isCalculatingRoute) Icons.Default.Navigation else Icons.Default.Check,
          contentDescription = null,
          tint = if (isCalculatingRoute) WarningAmber else NeonEmerald,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    // Location note + rank badge / rejection reason.
    Text(
      text = zone.locationNote,
      fontSize = 9.sp,
      color = TacticalOnSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    // Feasibility badge: ranked score or the rejection reason.
    if (evaluation != null) {
      if (evaluation.isFeasible) {
        Text(
          text = "MATCH ${evaluation.score}/100 • ${evaluation.capacityReport.statusLabel.uppercase()}",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = if (isFull) EmergencyRedBright else NeonEmerald,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = EmergencyRedBright,
            modifier = Modifier.size(11.dp)
          )
          Text(
            text = evaluation.rejectionReason?.label ?: "Not recommended",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = EmergencyRedBright,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }

    // Distance / walking ETA row.
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = OsrmRoutingService.formatDistance(distanceKm),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TacticalCyan
      )
      Text(
        text = "~${etaMins} min walk",
        fontSize = 10.sp,
        color = TacticalOnSurfaceVariant
      )
      Spacer(modifier = Modifier.weight(1f))
      if (evaluation?.hazardExposureCount ?: 0 > 0) {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = WarningAmber,
          modifier = Modifier.size(12.dp)
        )
      }
    }

    // Capacity bar: available/total + occupancy %.
    LinearProgressIndicator(
      progress = { occupancyRatio },
      modifier = Modifier
        .fillMaxWidth()
        .height(4.dp)
        .clip(RoundedCornerShape(2.dp)),
      color = if (isFull) EmergencyRed else NeonEmerald,
      trackColor = ObsidianContainerHigh
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "${(occupancyRatio * 100).roundToInt()}% full • ${zone.availableCapacity}/${zone.capacityTotal} spots free",
        fontSize = 9.sp,
        color = TacticalOnSurfaceVariant,
        maxLines = 1
      )
      Text(
        text = if (isFull) "FULL" else "OPEN",
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = if (isFull) EmergencyRedBright else NeonEmerald
      )
    }

    // Facilities strip (compact chips).
    Text(
      text = listOfNotNull(
        "Water".takeIf { zone.waterAvailable },
        "Food".takeIf { zone.foodAvailable },
        "Power".takeIf { zone.electricityAvailable },
        "Medical".takeIf { zone.medicalSupport },
        "Sanitation".takeIf { zone.sanitationAvailable },
        "Women & children".takeIf { zone.womenChildrenSuitability }
      ).joinToString(" • ").ifEmpty { "No resource flags set" },
      fontSize = 9.sp,
      color = TacticalOnSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      lineHeight = 12.sp
    )

    // Why this zone (top ranking reason) — only for feasible cards.
    if (evaluation != null && evaluation.isFeasible) {
      Text(
        text = evaluation.rankExplanation,
        fontSize = 9.sp,
        color = TacticalOnSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 11.sp
      )
    }

    // Route action.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(
          if (isSelected) NeonEmerald.copy(alpha = 0.18f)
          else if (isFull) ObsidianContainerHigh
          else NeonEmeraldContainer.copy(alpha = 0.25f)
        )
        .border(
          1.dp,
          if (isSelected) NeonEmerald else TacticalOutlineVariant.copy(alpha = 0.4f),
          RoundedCornerShape(8.dp)
        )
        .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = when {
          isCalculatingRoute -> "Calculating OSRM route..."
          isSelected -> "Routing to this zone"
          isFull -> "Full — pick another zone"
          else -> "Set as destination"
        },
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = if (isSelected) NeonEmerald else TacticalOnSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false)
      )
      Icon(
        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Navigation,
        contentDescription = null,
        tint = if (isSelected) NeonEmerald else TacticalOnSurfaceVariant,
        modifier = Modifier.size(13.dp)
      )
    }
  }
}

// ============================================================================
// COMPACT WEATHER ROW — temp / rainfall / wind / 3-hr trend in one line
// (SIMULATED pilot readings — labeled SIM DATA until a live IMD feed lands)
// ============================================================================

@Composable
private fun CompactWeatherRow(weather: WeatherMetrics) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainerLow.copy(alpha = 0.96f))
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
      .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // Honest label: these readings are SIMULATED pilot data, not live IMD values.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "SIM",
        fontSize = 8.sp,
        fontWeight = FontWeight.Black,
        color = WarningAmber,
        letterSpacing = 0.5.sp
      )
      Text(
        text = "DATA",
        fontSize = 8.sp,
        fontWeight = FontWeight.Black,
        color = WarningAmber,
        letterSpacing = 0.5.sp
      )
    }
    WeatherCell(
      icon = Icons.Default.Thermostat,
      label = "TEMP",
      value = weather.currentTemp,
      tint = TacticalCyan,
      modifier = Modifier.weight(1f)
    )
    WeatherCell(
      icon = Icons.Default.Opacity,
      label = "RAIN",
      value = weather.rainfallIntensity,
      tint = WarningAmber,
      modifier = Modifier.weight(1f)
    )
    WeatherCell(
      icon = Icons.Default.Air,
      label = "WIND",
      value = weather.windGust,
      tint = TacticalCyan,
      modifier = Modifier.weight(1f)
    )
    Row(
      modifier = Modifier.weight(1.2f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = if (weather.trend3h.contains("Worsen", ignoreCase = true)) {
          Icons.Default.TrendingDown
        } else {
          Icons.Default.TrendingUp
        },
        contentDescription = null,
        tint = if (weather.trend3h.contains("Worsen", ignoreCase = true)) EmergencyRedBright else NeonEmerald,
        modifier = Modifier.size(15.dp)
      )
      Column {
        Text(
          text = "3-HR TREND",
          fontSize = 8.sp,
          fontWeight = FontWeight.Black,
          color = TacticalOnSurfaceVariant,
          letterSpacing = 0.5.sp
        )
        Text(
          text = weather.trend3h,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = if (weather.trend3h.contains("Worsen", ignoreCase = true)) EmergencyRedBright else NeonEmerald,
          maxLines = 1
        )
      }
    }
  }
}

@Composable
private fun WeatherCell(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  value: String,
  tint: Color,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
    Column {
      Text(
        text = label,
        fontSize = 8.sp,
        fontWeight = FontWeight.Black,
        color = TacticalOnSurfaceVariant,
        letterSpacing = 0.5.sp
      )
      Text(
        text = value,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = TacticalOnSurface,
        maxLines = 1
      )
    }
  }
}

// ============================================================================
// PERSONAL RISK STRIP — RED/ORANGE/YELLOW/GREEN + understandable explanation
// ============================================================================

@Composable
private fun riskColor(level: RiskLevel?): Color = when (level) {
  RiskLevel.RED -> EmergencyRedBright
  RiskLevel.ORANGE -> WarningAmber
  RiskLevel.YELLOW -> Color(0xFFFDD835) // semantic risk-yellow, constant on both themes
  RiskLevel.GREEN -> NeonEmerald
  null -> TacticalOnSurfaceVariant
}

@Composable
private fun PersonalRiskStrip(
  risk: com.example.data.risk.PersonalRiskAssessment?,
  isFallbackLocation: Boolean,
  modifier: Modifier = Modifier
) {
  val color = riskColor(risk?.level)
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainerLowest.copy(alpha = 0.94f))
      .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
      .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(
      modifier = Modifier
        .size(26.dp)
        .clip(CircleShape)
        .background(color.copy(alpha = 0.2f))
        .border(1.5.dp, color, CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = risk?.level?.label?.first()?.toString() ?: "-",
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        color = color
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "RISK ${risk?.level?.label ?: "..."}",
          fontSize = 10.sp,
          fontWeight = FontWeight.Black,
          color = color,
          letterSpacing = 0.5.sp,
          maxLines = 1
        )
        Text(
          text = if (isFallbackLocation) "• INDIA FALLBACK" else "• DEVICE GPS",
          fontSize = 8.sp,
          fontWeight = FontWeight.Bold,
          color = if (isFallbackLocation) TacticalCyan else NeonEmerald,
          maxLines = 1
        )
      }
      Text(
        text = risk?.explanation ?: "Analyzing hazards around your location...",
        fontSize = 9.sp,
        color = TacticalOnSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 11.sp
      )
    }
  }
}

// ============================================================================
// WHAT SHOULD I DO? — actionable recommendation with WHY
// ============================================================================

@Composable
private fun RecommendedActionCard(
  action: com.example.data.risk.RecommendedAction?,
  selectedZoneName: String?,
  onWhyThisZone: () -> Unit,
  isCalculating: Boolean
) {
  if (action == null) return
  val isCritical = action.actionId in setOf(
    "EVACUATE_NOW", "MOVE_TO_HIGHER_GROUND", "MOVE_AWAY_FROM_RIVER"
  )
  val accent = if (isCritical) EmergencyRedBright else NeonEmerald
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(ObsidianContainerLowest.copy(alpha = 0.95f))
      .border(1.5.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(
        imageVector = if (isCritical) Icons.Default.Warning else Icons.Outlined.Shield,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(18.dp)
      )
      Text(
        text = "WHAT SHOULD I DO?",
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        color = TacticalOnSurfaceVariant,
        letterSpacing = 0.8.sp
      )
      if (isCalculating) {
        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = accent)
      }
    }
    Text(
      text = action.title,
      fontSize = 15.sp,
      fontWeight = FontWeight.Black,
      color = accent,
      letterSpacing = 0.4.sp
    )
    Text(
      text = action.explanation,
      fontSize = 10.sp,
      color = TacticalOnSurface,
      lineHeight = 13.sp
    )
    if (action.hasEvacuationTarget && selectedZoneName != null) {
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(ObsidianContainerHigh)
          .border(1.dp, NeonEmerald.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
          .clickable { onWhyThisZone() }
          .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(Icons.Default.NearMe, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(13.dp))
        Text(
          text = "Why this zone? Best pick — $selectedZoneName",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = NeonEmerald,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

// ============================================================================
// ROUTE INTELLIGENCE — distance, ETA, mode, safety, warnings, capacity, alt.
// OSRM VALIDATION is shown ONLY when the displayed route actually came from
// the live OSRM service (RouteResult.isLiveOsrm == true).
// ============================================================================

@Composable
private fun RouteIntelligencePanel(
  uiState: VippattiUiState,
  onSetTravelMode: (String) -> Unit,
  onLoadAlternativeRoutes: () -> Unit,
  onSelectBestSafeZone: () -> Unit
) {
  val route = uiState.activeRoute
  val evaluation = uiState.selectedEvaluation

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(ObsidianContainerLow.copy(alpha = 0.96f))
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // Header: destination + OSRM validation status (honest).
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "ROUTE TO",
          fontSize = 9.sp,
          fontWeight = FontWeight.Black,
          color = TacticalOnSurfaceVariant,
          letterSpacing = 0.8.sp
        )
        Text(
          text = uiState.selectedSafeZone?.name ?: "No safe zone selected",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Box(
        modifier = Modifier
          .clip(CircleShape)
          .background(
            when {
              route == null -> ObsidianContainerHigh
              route.isLiveOsrm -> NeonEmerald.copy(alpha = 0.15f)
              else -> ObsidianContainerHigh
            }
          )
          .border(
            1.dp,
            when {
              route == null -> TacticalOutlineVariant
              route.isLiveOsrm -> NeonEmerald
              else -> WarningAmber.copy(alpha = 0.7f)
            },
            CircleShape
          )
          .padding(horizontal = 8.dp, vertical = 3.dp)
          .testTag("osrm_validation_badge")
      ) {
        Text(
          text = when {
            route == null -> "NO ROUTE"
            route.isLiveOsrm -> "OSRM VALIDATED"
            else -> "OSRM OFFLINE EST."
          },
          fontSize = 8.sp,
          fontWeight = FontWeight.Bold,
          color = when {
            route == null -> TacticalOnSurfaceVariant
            route.isLiveOsrm -> NeonEmerald
            else -> WarningAmber
          }
        )
      }
    }

    // Metrics row: distance, ETA, mode, capacity.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      RouteMetric(
        label = "DISTANCE",
        value = route?.let { OsrmRoutingService.formatDistance(it.distanceMeters) } ?: "--",
        accent = TacticalCyan
      )
      RouteMetric(
        label = "ETA",
        value = route?.let { OsrmRoutingService.formatDuration(it.durationSeconds) } ?: "--",
        accent = NeonEmerald
      )
      RouteMetric(
        label = "MODE",
        value = if (uiState.travelMode == "driving") "VEHICLE" else "WALKING",
        accent = TacticalOnSurface
      )
      RouteMetric(
        label = "CAPACITY",
        value = evaluation?.let { "${it.capacityReport.availableCapacity} free" } ?: "--",
        accent = if ((evaluation?.capacityReport?.availableCapacity ?: 0) > 0) NeonEmerald else EmergencyRedBright
      )
    }

    // Route safety status + score bar.
    if (route != null) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "ROUTE SAFETY: ${route.routeSafetyStatus.label.uppercase()}",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = when (route.routeSafetyStatus) {
            RouteSafetyStatus.SAFE -> NeonEmerald
            RouteSafetyStatus.CAUTION -> WarningAmber
            RouteSafetyStatus.DANGER -> EmergencyRedBright
          },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "Safety ${route.routeSafetyScore}/100",
          fontSize = 10.sp,
          color = TacticalOnSurfaceVariant
        )
      }
      LinearProgressIndicator(
        progress = { route.routeSafetyScore / 100f },
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
        color = when (route.routeSafetyStatus) {
          RouteSafetyStatus.SAFE -> NeonEmerald
          RouteSafetyStatus.CAUTION -> WarningAmber
          RouteSafetyStatus.DANGER -> EmergencyRed
        },
        trackColor = ObsidianContainerHigh
      )

      // Hazard warnings — surfaced when the route meets a hazard zone.
      route.hazardWarnings.forEach { warning ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
              if (warning.isBlocking) EmergencyRedContainer.copy(alpha = 0.35f)
              else ObsidianContainer.copy(alpha = 0.8f)
            )
            .border(
              1.dp,
              if (warning.isBlocking) EmergencyRed.copy(alpha = 0.6f) else WarningAmber.copy(alpha = 0.4f),
              RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.Top
        ) {
          Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = if (warning.isBlocking) EmergencyRedBright else WarningAmber,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = warning.message,
            fontSize = 10.sp,
            color = if (warning.isBlocking) OnEmergencyRedContainer else TacticalOnSurface,
            lineHeight = 13.sp
          )
        }
      }
    }

    // Controls: travel mode toggle + alternatives + best-zone.
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Walking / Driving toggle.
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(ObsidianContainer)
          .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(8.dp))
          .padding(2.dp)
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (uiState.travelMode == "foot") NeonEmerald else Color.Transparent)
            .clickable { onSetTravelMode("foot") }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("mode_walking_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
            contentDescription = "Walking",
            tint = if (uiState.travelMode == "foot") OnNeonEmerald else TacticalOnSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (uiState.travelMode == "driving") NeonEmerald else Color.Transparent)
            .clickable { onSetTravelMode("driving") }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("mode_driving_button"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.DirectionsCar,
            contentDescription = "Driving",
            tint = if (uiState.travelMode == "driving") OnNeonEmerald else TacticalOnSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Button(
        onClick = onLoadAlternativeRoutes,
        colors = ButtonDefaults.buttonColors(
          containerColor = ObsidianContainerHigh,
          contentColor = TacticalCyan
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(34.dp).testTag("alternative_routes_button")
      ) {
        Icon(Icons.Default.AltRoute, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Alternatives (${uiState.alternativeRoutes.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
      }

      Spacer(modifier = Modifier.weight(1f))

      Button(
        onClick = onSelectBestSafeZone,
        colors = ButtonDefaults.buttonColors(
          containerColor = NeonEmeraldContainer,
          contentColor = OnNeonEmerald
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(34.dp).testTag("select_best_safe_zone_button")
      ) {
        Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Best Zone", fontSize = 10.sp, fontWeight = FontWeight.Bold)
      }
    }

    // Alternative corridor chips (when computed).
    if (uiState.alternativeRoutes.size > 1) {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(uiState.alternativeRoutes.size) { idx ->
          val alt = uiState.alternativeRoutes[idx]
          val isPrimary = route?.let { primary -> alt === primary } ?: (idx == 0)
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (isPrimary) NeonEmerald.copy(alpha = 0.15f) else ObsidianContainer)
              .border(
                1.dp,
                if (isPrimary) NeonEmerald else TacticalOutlineVariant,
                RoundedCornerShape(8.dp)
              )
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = "${alt.summary} • ${OsrmRoutingService.formatDistance(alt.distanceMeters)} • Safety ${alt.routeSafetyScore}",
              fontSize = 9.sp,
              color = if (isPrimary) NeonEmerald else TacticalOnSurfaceVariant,
              fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Normal,
              maxLines = 1
            )
          }
        }
      }
    }
  }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.RouteMetric(label: String, value: String, accent: Color) {
  Column(
    modifier = Modifier
      .weight(1f)
      .clip(RoundedCornerShape(10.dp))
      .background(ObsidianContainer.copy(alpha = 0.7f))
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
      .padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = TacticalOnSurfaceVariant, maxLines = 1)
    Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
  }
}

// ============================================================================
// EVACUATION CTA — large one-hand control, always bound to the SELECTED zone
// ============================================================================

@Composable
private fun EvacuationCta(
  uiState: VippattiUiState,
  onStartEvacuation: () -> Unit,
  onStopEvacuation: () -> Unit
) {
  val route = uiState.activeRoute
  val distanceStr = route?.let { OsrmRoutingService.formatDistance(it.distanceMeters) } ?: "--"
  val durationStr = route?.let { OsrmRoutingService.formatDuration(it.durationSeconds) } ?: "--"

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(if (uiState.isNavigatingLive) Color(0xFF0284C7) else NeonEmerald)
      .clickable {
        if (uiState.isNavigatingLive) onStopEvacuation() else onStartEvacuation()
      }
      .padding(horizontal = 14.dp, vertical = 12.dp)
      .testTag("start_evacuation_route_button"),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(if (uiState.isNavigatingLive) Color.White else OnNeonEmerald),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (uiState.isNavigatingLive) Icons.Default.Check else Icons.Default.Navigation,
          contentDescription = null,
          tint = if (uiState.isNavigatingLive) Color(0xFF0284C7) else NeonEmerald,
          modifier = Modifier.size(20.dp)
        )
      }
      Column {
        Text(
          text = if (uiState.isNavigatingLive) "ACTIVE GUIDANCE RUNNING" else "START EVACUATION ROUTE",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = if (uiState.isNavigatingLive) Color.White.copy(alpha = 0.85f) else OnNeonEmerald.copy(alpha = 0.85f),
          letterSpacing = 0.8.sp
        )
        Text(
          text = "To ${uiState.selectedSafeZone?.name ?: "selected safe zone"}",
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = if (uiState.isNavigatingLive) Color.White else OnNeonEmerald,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
    Column(horizontalAlignment = Alignment.End) {
      Text(distanceStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (uiState.isNavigatingLive) Color.White else OnNeonEmerald)
      Text(durationStr, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (uiState.isNavigatingLive) Color.White.copy(alpha = 0.8f) else OnNeonEmerald.copy(alpha = 0.8f))
    }
  }
}

// ============================================================================
// LIVE TURN-BY-TURN HUD — guidance always follows the SELECTED destination
// ============================================================================

@Composable
private fun LiveNavigationHud(
  uiState: VippattiUiState,
  onNextNavigationStep: () -> Unit,
  onStopEvacuation: () -> Unit
) {
  val route = uiState.activeRoute
  val currentStep = route?.steps?.getOrNull(uiState.currentNavigationStepIndex)
  val totalSteps = route?.steps?.size ?: 1

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 10.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainerLowest.copy(alpha = 0.96f))
      .border(1.dp, NeonEmerald, RoundedCornerShape(12.dp))
      .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(NeonEmerald),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = null,
            tint = OnNeonEmerald,
            modifier = Modifier.size(18.dp)
          )
        }
        Column {
          Text(
            text = "STEP ${uiState.currentNavigationStepIndex + 1} OF $totalSteps",
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = NeonEmerald,
            letterSpacing = 0.5.sp
          )
          Text(
            text = currentStep?.instruction ?: "Proceed along the safe corridor",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
          onClick = onNextNavigationStep,
          colors = ButtonDefaults.buttonColors(
            containerColor = NeonEmerald,
            contentColor = OnNeonEmerald
          ),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.height(30.dp).testTag("navigation_next_step_button")
        ) {
          Text(
            text = if (uiState.currentNavigationStepIndex + 1 >= totalSteps) "Arrive" else "Next",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
          )
        }
        IconButton(
          onClick = onStopEvacuation,
          modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(EmergencyRed)
            .testTag("navigation_stop_button")
        ) {
          Icon(Icons.Default.Close, contentDescription = "Exit Navigation", tint = Color.White, modifier = Modifier.size(14.dp))
        }
      }
    }
  }
}
