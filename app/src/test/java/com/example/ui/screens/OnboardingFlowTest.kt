package com.example.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Navigation contracts for the five-page onboarding flow: initial page,
 * forward/back movement, live pagination, and finish semantics. The finish
 * callback must fire exactly for skip / sign-in / final-page completion —
 * never merely for changing pages.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class OnboardingFlowTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var finished = 0

  private fun render() {
    finished = 0
    composeTestRule.setContent {
      VippattiTheme {
        OnboardingFlowScreen(onFinish = { finished++ })
      }
    }
  }

  /** Advances from page 1 through [clicks] primary actions. */
  private fun advance(clicks: Int) {
    composeTestRule.onNodeWithTag("onboarding_get_started").performClick()
    repeat(clicks - 1) {
      composeTestRule.onNodeWithTag("onboarding_primary").performClick()
    }
  }

  @Test
  fun `page 1 is displayed initially as Step 1 of 5`() {
    render()

    composeTestRule.onNodeWithText("SAFER PEOPLE. STRONGER COMMUNITIES.").assertExists()
    composeTestRule.onNodeWithContentDescription("Step 1 of 5").assertExists()
    assertEquals(0, finished)
  }

  @Test
  fun `page 1 advances to page 2`() {
    render()

    composeTestRule.onNodeWithTag("onboarding_get_started").performClick()

    composeTestRule.onNodeWithText("Understand risk in your area").assertExists()
    composeTestRule.onNodeWithText("Step 2 of 5").assertExists()
    assertEquals(0, finished)
  }

  @Test
  fun `every page can be reached in order`() {
    render()

    advance(1)
    composeTestRule.onNodeWithText("Understand risk in your area").assertExists()
    advanceToNext()
    composeTestRule.onNodeWithText("Prepare and respond with confidence").assertExists()
    advanceToNext()
    composeTestRule.onNodeWithText("Stay alert when seconds count").assertExists()
    advanceToNext()
    composeTestRule.onNodeWithText("You're ready to explore.").assertExists()
    assertEquals(0, finished)
  }

  private fun advanceToNext() {
    composeTestRule.onNodeWithTag("onboarding_primary").performClick()
  }

  @Test
  fun `the pagination indicator reflects the current page`() {
    render()

    composeTestRule.onNodeWithContentDescription("Step 1 of 5").assertExists()
    advance(1)
    composeTestRule.onNodeWithText("Step 2 of 5").assertExists()
    composeTestRule.onNodeWithContentDescription("Step 2 of 5").assertExists()
    advanceToNext()
    composeTestRule.onNodeWithText("Step 3 of 5").assertExists()
    advanceToNext()
    composeTestRule.onNodeWithText("Step 4 of 5").assertExists()
    advanceToNext()
    composeTestRule.onNodeWithText("Step 5 of 5").assertExists()
  }

  @Test
  fun `back navigation returns to the previous page`() {
    render()
    advance(1)
    composeTestRule.onNodeWithText("Understand risk in your area").assertExists()

    composeTestRule.onNodeWithTag("onboarding_back").performClick()
    composeTestRule.onNodeWithText("SAFER PEOPLE. STRONGER COMMUNITIES.").assertExists()

    // Deeper: page 4 back lands on page 3.
    advance(1)
    advanceToNext()
    advanceToNext()
    composeTestRule.onNodeWithText("Stay alert when seconds count").assertExists()
    composeTestRule.onNodeWithTag("onboarding_back").performClick()
    composeTestRule.onNodeWithText("Prepare and respond with confidence").assertExists()
    assertEquals(0, finished)
  }

  @Test
  fun `skip opens login without visiting every page`() {
    render()
    advance(1)
    advanceToNext()

    composeTestRule.onNodeWithTag("onboarding_skip").performClick()

    assertEquals(1, finished)
  }

  @Test
  fun `sign in opens login from page 1`() {
    render()

    composeTestRule.onNodeWithTag("onboarding_sign_in").performClick()

    assertEquals(1, finished)
  }

  @Test
  fun `completing page 5 opens login`() {
    render()
    advance(1)
    advanceToNext()
    advanceToNext()
    advanceToNext()
    composeTestRule.onNodeWithText("You're ready to explore.").assertExists()

    composeTestRule.onNodeWithTag("onboarding_primary").performClick()

    assertEquals(1, finished)
  }
}
