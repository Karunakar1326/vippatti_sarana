package com.example.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.VippattiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Render contracts for the welcome onboarding page: reference copy, feature
 * list, position indicator, actions, and callback wiring. No capability
 * overclaims may appear in the copy.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class OnboardingWelcomeTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var started = 0
  private var signedIn = 0
  private var skipped = 0

  private fun render() {
    started = 0
    signedIn = 0
    skipped = 0
    composeTestRule.setContent {
      VippattiTheme {
        OnboardingWelcomeScreen(
          onGetStarted = { started++ },
          onSignIn = { signedIn++ },
          onSkip = { skipped++ }
        )
      }
    }
  }

  @Test
  fun `hero copy matches the reference wording`() {
    render()

    composeTestRule.onNodeWithText("SAFER PEOPLE. STRONGER COMMUNITIES.").assertExists()
    composeTestRule.onNodeWithText("Vippatti Sarana").assertExists()
    composeTestRule.onNodeWithText(
      "Disaster preparedness for a safer, more resilient India."
    ).assertExists()
    composeTestRule.onNodeWithTag("onboarding_hero_logo").assertExists()
  }

  @Test
  fun `three features are listed with honest descriptions`() {
    render()

    composeTestRule.onNodeWithText("Reliable information").assertExists()
    composeTestRule.onNodeWithText(
      "Explore available hazard updates, advisories, and safety resources."
    ).assertExists()
    composeTestRule.onNodeWithText("Be prepared").assertExists()
    composeTestRule.onNodeWithText(
      "Learn essential safety practices, plan ahead, and access emergency resources."
    ).assertExists()
    composeTestRule.onNodeWithText("A safer tomorrow").assertExists()
    composeTestRule.onNodeWithText(
      "Build preparedness and support informed decisions for individuals and communities."
    ).assertExists()
  }

  @Test
  fun `position indicator and actions are present and wired`() {
    render()

    composeTestRule.onNodeWithTag("onboarding_pager").assertExists()
    composeTestRule.onNodeWithTag("onboarding_get_started").assertExists()
    composeTestRule.onNodeWithText("Get Started").assertExists()
    composeTestRule.onNodeWithTag("onboarding_sign_in").assertExists()
    composeTestRule.onNodeWithTag("onboarding_skip").assertExists()

    composeTestRule.onNodeWithTag("onboarding_get_started").performClick()
    composeTestRule.onNodeWithTag("onboarding_sign_in").performClick()
    composeTestRule.onNodeWithTag("onboarding_skip").performClick()
    assertEquals(1, started)
    assertEquals(1, signedIn)
    assertEquals(1, skipped)
  }

  @Test
  fun `no protection or response guarantees are claimed`() {
    render()

    for (overclaim in listOf(
      "real-time protection", "guaranteed", "always-active",
      "automatic emergency response", "offline broadcast"
    )) {
      composeTestRule.onAllNodesWithText(overclaim, substring = true, ignoreCase = true)
        .assertCountEquals(0)
    }
  }
}
