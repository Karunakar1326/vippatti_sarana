package com.example.ui.screens

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Persistence contracts for the onboarding completion flag: unread means
 * incomplete, and the flag survives re-reads once written. Page changes
 * never touch this class — only skip / sign-in / final completion do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingCompletionTest {

  private fun freshPrefs() = org.robolectric.RuntimeEnvironment.getApplication()
    .getSharedPreferences("test_onboarding_${System.nanoTime()}", Context.MODE_PRIVATE)

  @Test
  fun `a fresh install is not completed`() {
    assertFalse(OnboardingCompletion(freshPrefs()).isCompleted())
  }

  @Test
  fun `setCompleted persists across instances`() {
    val prefs = freshPrefs()
    OnboardingCompletion(prefs).setCompleted()

    assertTrue(OnboardingCompletion(prefs).isCompleted())
  }
}
