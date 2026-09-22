package com.example.viewmodel

import com.example.data.auth.AuthRepository
import com.example.data.auth.InMemoryAuthStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rotation-logout regression tests (Issue 1).
 *
 * Root cause: the login gate built its [AuthRepository] with `remember {}`
 * inside `setContent`, so every Activity recreation (rotation) created a NEW
 * repository whose in-process session flag (`processLoggedIn`) was false.
 * Users who logged in WITHOUT "stay signed in" were bounced to the login
 * screen on every rotation. The fix retains ONE repository inside
 * [AuthGateViewModel] (a [androidx.lifecycle.ViewModel] survives
 * configuration changes but not process death).
 *
 * These are plain JVM tests: they exercise the exact seam (instance
 * retention) without rendering the map screen (not unit-renderable).
 */
class AuthGateViewModelTest {

  private fun freshStorage() = InMemoryAuthStorage()

  private fun loginNoStay(storage: InMemoryAuthStorage): AuthRepository {
    val repo = AuthRepository(storage).apply { seedDemoAccount() }
    val result = repo.login(
      AuthRepository.DEMO_EMAIL, AuthRepository.DEMO_PASSWORD, staySignedIn = false
    )
    assertTrue("demo login should succeed: ${result.error}", result.ok)
    return repo
  }

  @Test
  fun `retained gate keeps the no-stay session across recreation reads`() {
    val gate = AuthGateViewModel(loginNoStay(freshStorage()))
    // First composition gate check…
    assertTrue(gate.isSignedIn())
    // …and every re-read after Activity recreation hits the SAME retained
    // repository, so the in-process session survives rotation.
    assertTrue("rotation must not log the user out", gate.isSignedIn())
    assertTrue(gate.isSignedIn())
    assertEquals(AuthRepository.DEMO_EMAIL, gate.currentUserEmail)
  }

  @Test
  fun `fresh repository per composition loses the session, reproducing the old bug`() {
    val storage = freshStorage()
    loginNoStay(storage)
    // This is what the old `remember { AuthRepository(...) }` gate did on
    // every recreation: a brand-new instance over the same store.
    val recreatedGateRepo = AuthRepository(storage)
    assertFalse(
      "a fresh repository must NOT see the no-stay session (old logout-on-rotate)",
      recreatedGateRepo.isLoggedIn()
    )
  }

  @Test
  fun `explicit logout signs out on the retained gate`() {
    val gate = AuthGateViewModel(loginNoStay(freshStorage()))
    assertTrue(gate.isSignedIn())
    gate.logout()
    assertFalse("explicit logout must sign out", gate.isSignedIn())
    // A later recreation must stay signed out too.
    assertFalse(gate.isSignedIn())
  }

  @Test
  fun `process death with stay-signed-in false signs out`() {
    val storage = freshStorage()
    loginNoStay(storage)
    // Process death destroys the ViewModel: a fresh gate + fresh repository
    // over the same persisted store must NOT restore the session.
    val afterDeath = AuthGateViewModel(AuthRepository(storage))
    assertFalse(afterDeath.isSignedIn())
  }

  @Test
  fun `stay-signed-in true survives process death`() {
    val storage = freshStorage()
    val repo = AuthRepository(storage).apply { seedDemoAccount() }
    assertTrue(
      repo.login(AuthRepository.DEMO_EMAIL, AuthRepository.DEMO_PASSWORD, staySignedIn = true).ok
    )
    val afterDeath = AuthGateViewModel(AuthRepository(storage))
    assertTrue(afterDeath.isSignedIn())
    assertEquals(AuthRepository.DEMO_EMAIL, afterDeath.currentUserEmail)
  }

  @Test
  fun `login and register delegate to the retained repository`() {
    val storage = freshStorage()
    val gate = AuthGateViewModel(AuthRepository(storage).apply { seedDemoAccount() })
    assertTrue(
      gate.login(AuthRepository.DEMO_EMAIL, AuthRepository.DEMO_PASSWORD, staySignedIn = false).ok
    )
    assertTrue(gate.isSignedIn())
    gate.logout()
    assertTrue(
      gate.register("newuser@example.com", "strongpass1", staySignedIn = true).ok
    )
    assertTrue(gate.isSignedIn())
    assertEquals("newuser@example.com", gate.currentUserEmail)
  }
}
