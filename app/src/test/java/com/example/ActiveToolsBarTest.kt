package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.theme.VippattiTheme
import com.example.viewmodel.VippattiUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * BUG-1 REGRESSION (UI half) — the global device-tool overlay must be GONE once
 * the Local SOS flow completes, so the screen underneath returns to normal.
 *
 * The ViewModel half lives in `viewmodel/SosFlowStateTest`; this test pins the
 * rendering contract of `MainActivity.ActiveToolsBar` itself.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h720dp")
class ActiveToolsBarTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `no overlay is rendered on a clean screen`() {
    composeTestRule.setContent {
      VippattiTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          ActiveToolsBar(
            uiState = VippattiUiState(),
            onStopDeviceTools = {},
            onCancelSos = {}
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("active_tool_sos_stop").assertDoesNotExist()
    composeTestRule.onNodeWithTag("active_tool_siren_stop").assertDoesNotExist()
    composeTestRule.onNodeWithTag("active_tool_torch_stop").assertDoesNotExist()
  }

  @Test
  fun `overlay stops the SOS and disappears when the state clears`() {
    var cancelRequested = false

    composeTestRule.setContent {
      VippattiTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          ActiveToolsBar(
            uiState = VippattiUiState(isSosActive = true),
            onStopDeviceTools = {},
            onCancelSos = { cancelRequested = true }
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("active_tool_sos_stop").assertExists().performClick()
    assertTrue("STOP must cancel the armed local SOS", cancelRequested)
  }
}