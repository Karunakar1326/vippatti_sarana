package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.routing.GeoPoint
import com.example.data.routing.RouteHazardWarning
import com.example.data.routing.RouteResult
import com.example.data.routing.RouteSafetyStatus
import com.example.ui.theme.VippattiTheme
import com.example.viewmodel.RouteStatus
import com.example.viewmodel.VippattiUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * BUG-2 REGRESSION — the Route Safety card must never balloon past the screen.
 *
 * Reported symptom: with a DANGER route the card ("Route To / ROUTE SAFETY:
 * DANGER — Route Enters Hazard Zone") grew so tall it broke the layout. This
 * test renders the exact worst case (DANGER + blocking hazard warning + router
 * failure with a Retry row + lifecycle message) on a small phone and asserts the
 * card height stays bounded while every information block is still present.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h720dp")
class RouteSafetyCardLayoutTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun dangerRouteState() = VippattiUiState(
    routeStatus = RouteStatus.NETWORK_ERROR,
    routeStatusMessage =
      "No road route received (offline or router unavailable). " +
        "Nothing is drawn — an offline estimate is only offered if you ask for it.",
    selectedSafeZone = com.example.data.model.SafeZone(
      id = "sz-test",
      name = "Munnar Higher Ground Relief Camp",
      lat = 10.06,
      lon = 77.06,
      locationNote = "Test",
      capacityTotal = 100,
      capacityCurrent = 40,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = false,
      sanitationAvailable = true,
      medicalSupport = false,
      accessibility = "Road",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record",
      elevationNote = "",
      provenance = com.example.data.model.DataProvenance(source = "test")
    ),
    activeRoute = RouteResult(
      distanceMeters = 3400.0,
      durationSeconds = 2400.0,
      pathPoints = listOf(GeoPoint(10.05, 77.05), GeoPoint(10.06, 77.06)),
      steps = emptyList(),
      isLiveOsrm = false,
      summary = "Offline Hazard-Skirting Corridor",
      travelMode = "foot",
      hazardWarnings = listOf(
        RouteHazardWarning(
          hazardName = "Test hazard",
          hazardTypeLabel = "Flood",
          message = "Danger — Route Enters Hazard Zone: the corridor crosses the simulated flood pocket.",
          isBlocking = true
        )
      ),
      routeSafetyStatus = RouteSafetyStatus.DANGER,
      routeSafetyScore = 25
    )
  )

  private fun renderPanel(state: VippattiUiState) {
    composeTestRule.setContent {
      VippattiTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          RouteIntelligencePanel(
            uiState = state,
            onSetTravelMode = {},
            onLoadAlternativeRoutes = {},
            onSelectBestSafeZone = {},
            onRequestFallbackRoute = {}
          )
        }
      }
    }
  }

  @Test
  fun `danger card stays bounded and keeps every information block`() {
    renderPanel(dangerRouteState())

    // All required information survives the compaction.
    composeTestRule.onNodeWithText("ROUTE TO").assertExists()
    composeTestRule.onNodeWithText("Munnar Higher Ground Relief Camp").assertExists()
    composeTestRule.onNodeWithTag("osrm_validation_badge").assertExists()
    composeTestRule.onNodeWithTag("route_status_message").assertExists()
    composeTestRule.onNodeWithText("Retry road route").assertExists()
    composeTestRule.onNodeWithText("Use unverified estimate").assertExists()
    composeTestRule.onNodeWithText("Safety 25/100").assertExists()

    // The card must stay usable on a 720dp-tall phone: bounded to < 2/3 height.
    val density = composeTestRule.density.density
    val cardHeightPx = composeTestRule.onNodeWithTag("route_intelligence_panel")
      .fetchSemanticsNode().size.height
    val maxAllowedPx = (480 * density).toInt()
    assertTrue(
      "route card must stay compact: $cardHeightPx px (max $maxAllowedPx px)",
      cardHeightPx < maxAllowedPx
    )
  }
}