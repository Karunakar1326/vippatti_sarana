package com.example.viewmodel

import androidx.lifecycle.ViewModel
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthResult

/**
 * Activity-scoped holder for the login gate's [AuthRepository].
 *
 * WHY THIS EXISTS (rotation logout fix): the repository keeps the in-process
 * session flag (`processLoggedIn`) that lets a user who did NOT check
 * "stay signed in" stay authenticated until an explicit logout. A repository
 * created with `remember {}` inside `setContent` is rebuilt on every
 * Activity recreation (rotation), dropping that flag and bouncing the user
 * back to [com.example.ui.screens.LoginScreen] — the reported
 * "logout on rotate". A [ViewModel] survives configuration changes, so the
 * SAME repository instance (and its in-process session) survives rotation,
 * while process death still creates a fresh instance that falls back to the
 * persisted store — explicit logout and the stay-signed-in restart rule are
 * unchanged.
 */
class AuthGateViewModel(val repository: AuthRepository) : ViewModel() {

  fun isSignedIn(): Boolean = repository.isLoggedIn()

  val currentUserEmail: String?
    get() = repository.currentUserEmail

  fun login(email: String, password: String, staySignedIn: Boolean): AuthResult =
    repository.login(email, password, staySignedIn)

  fun register(email: String, password: String, staySignedIn: Boolean): AuthResult =
    repository.register(email, password, staySignedIn)

  fun logout() = repository.logout()
}
