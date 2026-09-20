package com.example.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Standard JUnit rule that installs a controllable Main dispatcher for tests
 * that exercise `ViewModel.viewModelScope` (which is hard-bound to
 * `Dispatchers.Main.immediate`).
 *
 * [UnconfinedTestDispatcher] is used on purpose: it runs launched work eagerly
 * and synchronously, so a coroutine is never left "in flight" while Main is
 * being swapped in `finished` — the race that produced
 * "Dispatchers.Main is used concurrently with setting it" under Robolectric and
 * "Main dispatcher was absent" on a plain JVM. Remaining queued work (e.g. the
 * siren countdown) is drained before Main is reset.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {

  val dispatcher = UnconfinedTestDispatcher()

  override fun starting(description: Description) {
    Dispatchers.setMain(dispatcher)
  }

  override fun finished(description: Description) {
    dispatcher.scheduler.advanceUntilIdle()
    Dispatchers.resetMain()
  }
}