package com.example.data.auth

import com.example.data.auth.AuthError
import com.example.data.auth.AuthRepository
import com.example.data.auth.InMemoryAuthStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local email+password auth contracts (JVM tests use the in-memory store):
 * registration, hashed storage, correct/incorrect login, duplicate accounts,
 * input validation, the seeded demo account and the stay-signed-in restart
 * rule.
 */
class AuthRepositoryTest {

  private fun freshRepository(): AuthRepository {
    val repo = AuthRepository(InMemoryAuthStorage())
    repo.seedDemoAccount()
    return repo
  }

  @Test
  fun `demo account is seeded and logs in with the documented password`() {
    val repo = freshRepository()
    val result = repo.login(AuthRepository.DEMO_EMAIL, AuthRepository.DEMO_PASSWORD, staySignedIn = true)
    assertTrue("demo login should succeed: ${result.error}", result.ok)
    assertEquals(AuthRepository.DEMO_EMAIL, result.email)
    assertEquals(AuthRepository.DEMO_EMAIL, repo.currentUserEmail)
  }

  @Test
  fun `wrong password is rejected with WRONG_CREDENTIALS`() {
    val repo = freshRepository()
    val result = repo.login(AuthRepository.DEMO_EMAIL, "not-the-password", staySignedIn = true)
    assertFalse(result.ok)
    assertEquals(AuthError.WRONG_CREDENTIALS, result.error)
  }

  @Test
  fun `unknown email is reported as ACCOUNT_NOT_FOUND`() {
    val repo = freshRepository()
    val result = repo.login("nobody@example.com", "whatever123", staySignedIn = true)
    assertFalse(result.ok)
    assertEquals(AuthError.ACCOUNT_NOT_FOUND, result.error)
  }

  @Test
  fun `invalid email is rejected up front`() {
    val repo = freshRepository()
    assertFalse(repo.login("not-an-email", "whatever123", staySignedIn = true).ok)
    assertFalse(repo.register("nope@@", "whatever123", staySignedIn = true).ok)
  }

  @Test
  fun `weak password is rejected on registration`() {
    val repo = freshRepository()
    val result = repo.register("new.user@example.com", "123", staySignedIn = true)
    assertFalse(result.ok)
    assertEquals(AuthError.WEAK_PASSWORD, result.error)
  }

  @Test
  fun `registration then login round-trips`() {
    val repo = freshRepository()
    val registered = repo.register("new.user@example.com", "s3cret-pass", staySignedIn = true)
    assertTrue("register failed: ${registered.error}", registered.ok)
    repo.logout()

    val login = repo.login("new.user@example.com", "s3cret-pass", staySignedIn = true)
    assertTrue("login after register failed: ${login.error}", login.ok)
    assertEquals("new.user@example.com", repo.currentUserEmail)
  }

  @Test
  fun `duplicate registration is EMAIL_TAKEN`() {
    val repo = freshRepository()
    // Demo email is already seeded.
    val result = repo.register(AuthRepository.DEMO_EMAIL, "another-pass", staySignedIn = true)
    assertFalse(result.ok)
    assertEquals(AuthError.EMAIL_TAKEN, result.error)
  }

  @Test
  fun `logout clears the session but a fresh login restores it`() {
    val repo = freshRepository()
    repo.login(AuthRepository.DEMO_EMAIL, AuthRepository.DEMO_PASSWORD, staySignedIn = true)
    assertTrue(repo.isLoggedIn())
    repo.logout()
    assertFalse(repo.isLoggedIn())
    assertNull(repo.currentUserEmail)
  }

  @Test
  fun `stay signed in false does not persist the session across restarts`() {
    val repo = freshRepository()
    repo.login(AuthRepository.DEMO_EMAIL, AuthRepository.DEMO_PASSWORD, staySignedIn = false)
    assertTrue("should be logged in for the current process", repo.isLoggedIn())
    // Simulate a fresh process (a NEW repository over the SAME storage).
    val restarted = AuthRepository(InMemoryAuthStorage())
    restarted.seedDemoAccount()
    assertFalse("session must not survive a restart when stay-signed-in is off", restarted.isLoggedIn())
  }

  @Test
  fun `stay signed in true persists the session across restarts`() {
    val storage = InMemoryAuthStorage()
    val repo = AuthRepository(storage)
    repo.seedDemoAccount()
    repo.login(AuthRepository.DEMO_EMAIL, AuthRepository.DEMO_PASSWORD, staySignedIn = true)

    val restarted = AuthRepository(storage)
    assertTrue(restarted.isLoggedIn())
    assertEquals(AuthRepository.DEMO_EMAIL, restarted.currentUserEmail)
  }
}