package com.example.data.auth

import android.content.Context
import java.security.MessageDigest
import java.util.Locale
import kotlin.random.Random

/** Why a login/registration attempt failed (shown verbatim on the login page). */
enum class AuthError {
  INVALID_EMAIL,
  WEAK_PASSWORD,
  EMAIL_TAKEN,
  ACCOUNT_NOT_FOUND,
  WRONG_CREDENTIALS
}

/** Outcome of a login/registration attempt; [ok] false means [error] is set. */
data class AuthResult(
  val ok: Boolean,
  val email: String = "",
  val error: AuthError? = null
) {
  companion object {
    fun success(email: String) = AuthResult(ok = true, email = email)
    fun failure(error: AuthError) = AuthResult(ok = false, error = error)
  }
}

/**
 * Backing store for local credentials and the session flag. Abstracted so the
 * repository is unit-testable with an in-memory store (JVM tests have no
 * Android SharedPreferences).
 */
interface AuthStorage {
  fun isLoggedIn(): Boolean
  fun setLoggedIn(value: Boolean)
  fun isStaySignedIn(): Boolean
  fun setStaySignedIn(value: Boolean)
  fun lastEmail(): String?
  fun setLastEmail(value: String?)
  /** Stored "salt:hash" for [email], or null when no account exists. */
  fun readCredential(email: String): String?
  fun writeCredential(email: String, stored: String)
}

/** SharedPreferences-backed [AuthStorage] used in production. */
class SharedPrefsAuthStorage(context: Context) : AuthStorage {

  private val prefs = context.getSharedPreferences("vippatti_sarana_auth", Context.MODE_PRIVATE)

  private fun credKey(email: String) = "cred_${email.trim().lowercase(Locale.ROOT)}"

  override fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_LOGGED_IN, false)
  override fun setLoggedIn(value: Boolean) { prefs.edit().putBoolean(KEY_LOGGED_IN, value).apply() }

  override fun isStaySignedIn(): Boolean = prefs.getBoolean(KEY_STAY_SIGNED_IN, true)
  override fun setStaySignedIn(value: Boolean) { prefs.edit().putBoolean(KEY_STAY_SIGNED_IN, value).apply() }

  override fun lastEmail(): String? = prefs.getString(KEY_LAST_EMAIL, null)
  override fun setLastEmail(value: String?) {
    prefs.edit().putString(KEY_LAST_EMAIL, value).apply()
  }

  override fun readCredential(email: String): String? = prefs.getString(credKey(email), null)
  override fun writeCredential(email: String, stored: String) {
    prefs.edit().putString(credKey(email), stored).apply()
  }

  private companion object {
    const val KEY_LOGGED_IN = "logged_in"
    const val KEY_STAY_SIGNED_IN = "stay_signed_in"
    const val KEY_LAST_EMAIL = "last_email"
  }
}

/** In-memory [AuthStorage] for JVM unit tests. */
class InMemoryAuthStorage : AuthStorage {
  private val credentials = mutableMapOf<String, String>()
  private var loggedIn = false
  private var staySignedIn = true
  private var email: String? = null

  override fun isLoggedIn(): Boolean = loggedIn
  override fun setLoggedIn(value: Boolean) { loggedIn = value }

  override fun isStaySignedIn(): Boolean = staySignedIn
  override fun setStaySignedIn(value: Boolean) { staySignedIn = value }

  override fun lastEmail(): String? = email
  override fun setLastEmail(value: String?) { email = value }

  override fun readCredential(email: String): String? =
    credentials[email.trim().lowercase(Locale.ROOT)]

  override fun writeCredential(email: String, stored: String) {
    credentials[email.trim().lowercase(Locale.ROOT)] = stored
  }
}

/** Salted SHA-256 password hashing (pure JVM; no secret is stored in plaintext). */
object PasswordHasher {

  fun newSalt(): String = Random.Default.nextBytes(8).joinToString("") { "%02x".format(it) }

  fun hash(password: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update((salt + ":" + password).toByteArray(Charsets.UTF_8))
    return digest.digest().joinToString("") { "%02x".format(it) }
  }
}

/**
 * Local email + password auth. Runs entirely offline: accounts are seeded and
 * kept in app-local storage, hashed with a per-account salt. Works without
 * any network, which is exactly what a disaster-relief app must do.
 */
class AuthRepository(private val storage: AuthStorage) {

  /** True across app restarts ONLY when the user asked to stay signed in. */
  fun isLoggedIn(): Boolean =
    storage.isLoggedIn() && (storage.isStaySignedIn() || processLoggedIn)

  val currentUserEmail: String?
    get() = if (isLoggedIn()) storage.lastEmail() else null

  /** Marks the in-process session (for the stay-signed-in = false restart case). */
  private var processLoggedIn: Boolean = false

  fun login(email: String, password: String, staySignedIn: Boolean): AuthResult {
    val clean = normalizeEmail(email) ?: return AuthResult.failure(AuthError.INVALID_EMAIL)
    val stored = storage.readCredential(clean)
      ?: return AuthResult.failure(AuthError.ACCOUNT_NOT_FOUND)
    val separator = stored.indexOf(':')
    if (separator <= 0) return AuthResult.failure(AuthError.WRONG_CREDENTIALS)
    val salt = stored.substring(0, separator)
    val expected = stored.substring(separator + 1)
    if (!constantTimeEquals(PasswordHasher.hash(password, salt), expected)) {
      return AuthResult.failure(AuthError.WRONG_CREDENTIALS)
    }
    processLoggedIn = true
    storage.setLoggedIn(true)
    storage.setStaySignedIn(staySignedIn)
    storage.setLastEmail(clean)
    return AuthResult.success(clean)
  }

  fun register(email: String, password: String, staySignedIn: Boolean): AuthResult {
    val clean = normalizeEmail(email) ?: return AuthResult.failure(AuthError.INVALID_EMAIL)
    if (password.length < MIN_PASSWORD_LENGTH) {
      return AuthResult.failure(AuthError.WEAK_PASSWORD)
    }
    if (storage.readCredential(clean) != null) {
      return AuthResult.failure(AuthError.EMAIL_TAKEN)
    }
    val salt = PasswordHasher.newSalt()
    storage.writeCredential(clean, "$salt:${PasswordHasher.hash(password, salt)}")
    processLoggedIn = true
    storage.setLoggedIn(true)
    storage.setStaySignedIn(staySignedIn)
    storage.setLastEmail(clean)
    return AuthResult.success(clean)
  }

  fun logout() {
    processLoggedIn = false
    storage.setLoggedIn(false)
    // Keep lastEmail so the login page can prefill it next time.
  }

  /** Seeds the demo account on first run so the app is usable immediately. */
  fun seedDemoAccount() {
    if (storage.readCredential(DEMO_EMAIL) == null) {
      val salt = PasswordHasher.newSalt()
      storage.writeCredential(DEMO_EMAIL, "$salt:${PasswordHasher.hash(DEMO_PASSWORD, salt)}")
    }
  }

  private fun normalizeEmail(raw: String): String? {
    val email = raw.trim().lowercase(Locale.ROOT)
    return email.takeIf { EMAIL_REGEX.matches(it) }
  }

  private fun constantTimeEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
    return diff == 0
  }

  companion object {
    const val DEMO_EMAIL = "demo@vippatti.in"
    const val DEMO_PASSWORD = "vippatti123"
    const val MIN_PASSWORD_LENGTH = 6
    private val EMAIL_REGEX =
      Regex("^[A-Za-z0-9.+_-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
  }
}