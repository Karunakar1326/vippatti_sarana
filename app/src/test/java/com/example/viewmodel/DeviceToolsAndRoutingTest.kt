package com.example.viewmodel

import com.example.data.disaster.DisasterDataProvider
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.ProviderResult
import com.example.data.disaster.providers.FirmsFireProvider
import com.example.data.news.GNewsCall
import com.example.data.news.GNewsService
import com.example.data.news.MemoryNewsCache
import com.example.data.news.NewsError
import com.example.data.news.NewsErrorKind
import com.example.data.news.NewsRepository
import com.example.data.news.NewsScope
import com.example.data.reports.LocalEmergencyReportService
import com.example.viewmodel.RouteStatus.IDLE
import com.example.viewmodel.RouteStatus.REQUESTING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behaviour tests for the explicit emergency-tool and routing contracts
 * (device tools + route state machine + offline honesty). All network
 * boundaries are test fakes — no test hits the real network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceToolsAndRoutingTest {

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    // Drain every coroutine the ViewModel launched (its init pipeline runs on
    // viewModelScope, i.e. on Main) BEFORE resetMain — otherwise they try to
    // dispatch on a missing Main after the test ends and pollute the next test
    // with UncaughtExceptionsBeforeTest.
    testDispatcher.scheduler.advanceUntilIdle()
    Dispatchers.resetMain()
  }

  private fun viewModel() = VippattiViewModel(
    reportService = LocalEmergencyReportService(),
    newsCache = MemoryNewsCache(),
    disasterCache = MemoryDisasterCache(),
    disasterRepository = DisasterDataRepository(
      providers = listOf(FailingProvider(DisasterSource.USGS), FailingProvider(DisasterSource.IMD_CAP)),
      cache = MemoryDisasterCache()
    ),
    newsRepositoryOverride = NewsRepository(
      service = FailingNewsService(),
      cache = MemoryNewsCache(),
      apiKeyProvider = { "" }
    ),
    weatherFetcher = { null }
  )

  private class FailingProvider(override val providerId: DisasterSource) : DisasterDataProvider {
    override suspend fun fetchIndiaEvents(): ProviderResult =
      ProviderResult.Failure("No connection (test fake).")
  }

  private class FailingNewsService : GNewsService {
    override suspend fun search(scope: NewsScope, apiKey: String): GNewsCall =
      GNewsCall.Failure(NewsError(NewsErrorKind.NETWORK, "offline (test fake)"))
  }

  // ---------------------------------------------------------- DEVICE TOOLS

  @Test
  fun `siren state is explicit and counts down`() = runTest(testDispatcher) {
    val vm = viewModel()

    assertEquals(SirenState.IDLE, vm.uiState.value.sirenState)
    assertTrue(vm.uiState.value.isSirenOn.not())

    vm.startSiren()
    assertEquals(SirenState.PLAYING, vm.uiState.value.sirenState)
    assertTrue(vm.uiState.value.isSirenOn)
    assertEquals(SIREN_MAX_SECONDS, vm.uiState.value.sirenSecondsLeft)

    advanceTimeBy(5_000)
    // NOTE: deliberately NOT advanceUntilIdle — that would run the whole 60 s
    // countdown to completion. We assert the mid-countdown value instead.
    assertEquals(SIREN_MAX_SECONDS - 5, vm.uiState.value.sirenSecondsLeft)
    assertEquals(SirenState.PLAYING, vm.uiState.value.sirenState)
  }

  @Test
  fun `second tap always stops the siren`() = runTest(testDispatcher) {
    val vm = viewModel()
    vm.toggleSiren()
    assertEquals(SirenState.PLAYING, vm.uiState.value.sirenState)

    vm.toggleSiren()
    advanceUntilIdle()
    assertEquals(SirenState.IDLE, vm.uiState.value.sirenState)
    assertEquals(0, vm.uiState.value.sirenSecondsLeft)
    assertTrue(vm.uiState.value.isSirenOn.not())
  }

  @Test
  fun `torch reflects the reported platform outcome`() = runTest(testDispatcher) {
    val vm = viewModel()

    vm.toggleFlashlight()
    assertTrue(vm.uiState.value.isFlashlightOn)

    vm.onTorchResult(false, "Camera service refused (test fake).")
    assertEquals(TorchState.UNAVAILABLE, vm.uiState.value.torchState)
    assertTrue(vm.uiState.value.isFlashlightOn.not())
    assertNotNull(vm.uiState.value.torchMessage)

    vm.toggleFlashlight()
    vm.onTorchResult(false, TORCH_REASON_PERMISSION, permissionDenied = true)
    assertEquals(TorchState.PERMISSION_DENIED, vm.uiState.value.torchState)
    assertNotNull(vm.uiState.value.torchMessage)
  }

  @Test
  fun `global stop ends every device tool`() = runTest(testDispatcher) {
    val vm = viewModel()
    vm.toggleFlashlight()
    vm.toggleSiren()
    vm.stopAllDeviceTools()
    advanceUntilIdle()

    assertEquals(SirenState.IDLE, vm.uiState.value.sirenState)
    assertEquals(TorchState.OFF, vm.uiState.value.torchState)
    assertTrue(vm.uiState.value.hasActiveDeviceTool.not())
  }

  @Test
  fun `offline-first flag defaults to off and never claims a download`() = runTest(testDispatcher) {
    val vm = viewModel()
    assertEquals(false, vm.uiState.value.isOfflineFirstMode)

    vm.toggleOfflineCache(true)
    assertEquals(true, vm.uiState.value.isOfflineFirstMode)
    assertTrue(vm.uiState.value.snackbarMessage!!.contains("no bulk offline download"))
  }

  // ROUTING TESTS CONTINUE BELOW (marker)
  @Test
  fun `route request starts in REQUESTING with zero geometry drawn`() = runTest(testDispatcher) {
    val vm = viewModel()
    vm.toggleMockData()
    val best = vm.uiState.value.rankedShelters.firstOrNull()
    assertNotNull("demo network must offer a feasible shelter", best)

    vm.selectSafeZone(best!!.zone, autoRoute = false)
    vm.calculateRouteToSelectedZone()

    val state = vm.uiState.value
    // Nothing to draw yet: geometry only ever appears in READY/FALLBACK states.
    assertEquals(REQUESTING, state.routeStatus)
    assertNull("no straight-line placeholder may be drawn while requesting", state.activeRoute)
    assertTrue("request spinner state must be observable", state.isCalculatingRoute)
  }

  @Test
  fun `requesting then clearing never resurrects a route`() = runTest(testDispatcher) {
    val vm = viewModel()
    vm.toggleMockData()
    val best = vm.uiState.value.rankedShelters.firstOrNull() ?: return@runTest
    vm.selectSafeZone(best.zone, autoRoute = false)
    vm.calculateRouteToSelectedZone()
    vm.clearActiveRoute()

    advanceUntilIdle()
    val state = vm.uiState.value
    assertEquals(IDLE, state.routeStatus)
    assertNull(state.activeRoute)
    assertNull(state.routeStatusMessage)
  }

  @Test
  fun `explicit offline estimate is labelled unverified, never safe`() = runTest(testDispatcher) {
    val vm = viewModel()
    vm.toggleMockData()
    val best = vm.uiState.value.rankedShelters.firstOrNull() ?: return@runTest
    vm.selectSafeZone(best.zone, autoRoute = false)

    vm.requestOfflineFallbackRoute()

    val state = vm.uiState.value
    assertEquals(com.example.viewmodel.RouteStatus.FALLBACK_UNVERIFIED, state.routeStatus)
    assertNotNull("the explicitly requested estimate IS drawable", state.activeRoute)
    assertTrue("estimate is not a live road route", state.activeRoute!!.isLiveOsrm.not())
    assertTrue(
      "estimate must say it is unverified: ${state.routeStatusMessage}",
      state.routeStatusMessage!!.contains("NOT", ignoreCase = true)
    )
    assertTrue("estimate must never count as validated", state.hasValidatedRoute.not())
  }

  @Test
  fun `validated live geometry passes the hasValidatedRoute gate`() {
    val live = com.example.data.routing.RouteResult(
      distanceMeters = 1234.0,
      durationSeconds = 900.0,
      pathPoints = listOf(
        com.example.data.routing.GeoPoint(9.85, 76.94),
        com.example.data.routing.GeoPoint(9.86, 76.95)
      ),
      steps = emptyList(),
      isLiveOsrm = true,
      summary = "OSRM test route",
      travelMode = "foot"
    )
    val state = VippattiUiState(activeRoute = live, routeStatus = com.example.viewmodel.RouteStatus.READY)
    assertTrue(state.hasValidatedRoute)
    assertEquals(false, VippattiUiState(activeRoute = live, routeStatus = IDLE).hasValidatedRoute)
  }

  @Test
  fun `switching demo data off keeps real provider states`() = runTest(testDispatcher) {
    val vm = viewModel()
    vm.toggleMockData()
    assertEquals(false, vm.uiState.value.isMockDataVisible)
    assertTrue("live feed states survive the toggle", vm.uiState.value.providerStates.isNotEmpty())

    // And it is reversible.
    vm.toggleMockData()
    assertEquals(true, vm.uiState.value.isMockDataVisible)
  }
}