package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.EmergencyContact
import com.example.data.GoBagItem
import com.example.data.MockDisasterRepository
import com.example.data.PilotRegionData
import com.example.data.UserProfile
import com.example.data.WeatherMetrics
import com.example.data.reports.EmergencyReport
import com.example.data.reports.EmergencyReportService
import com.example.data.reports.NdrfEmergencyReportService
import com.example.data.reports.ReportKind
import com.example.data.reports.ReportReceipt
import com.example.data.model.GeoMath
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import com.example.data.routing.OsrmRoutingService
import com.example.data.routing.RouteResult
import com.example.data.risk.ActionAdvisor
import com.example.data.risk.PersonalRiskAssessment
import com.example.data.risk.RecommendedAction
import com.example.data.risk.RelocationPlan
import com.example.data.risk.RelocationPlanner
import com.example.data.risk.RiskAssessmentEngine
import com.example.data.shelters.SafeZoneEvaluation
import com.example.data.shelters.SafeZoneEvaluator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimum movement (meters) from the last route origin before GPS fixes may
 * trigger re-routing. Fixes arrive ~1/sec with sub-metre jitter; without this
 * guard every fix would cancel the in-flight OSRM request and the map would
 * starve (a route could never finish computing, so nothing was ever drawn).
 */
private const val REROUTE_MIN_MOVEMENT_METERS = 50.0

enum class ScreenTab {
  RADAR_MAP,
  NEWS_DISPATCHES,
  INSTRUCTIONS,
  PROFILE
}

/**
 * Central UI state. All intelligence results flow into this single state,
 * preserving the single primary flow:
 *   Compose UI -> ViewModel -> Repository/Services -> Location ->
 *   Hazard/Safe-Zone Intelligence -> OSRM Routing -> OSMDroid Map
 */
data class VippattiUiState(
  // --- App chrome ---
  val currentTab: ScreenTab = ScreenTab.RADAR_MAP,
  val isDarkTheme: Boolean = true,
  val is100PercentOfflineCached: Boolean = true,
  val lastSyncTime: String = "Today at 08:42 AM • Emergency SMS live",
  val isSyncing: Boolean = false,
  val isAudioPlaying: Boolean = false,
  val audioPlaybackSeconds: Int = 0,
  val selectedNewsCategory: String = "All",
  val goBagItems: List<GoBagItem> = MockDisasterRepository.defaultGoBagItems,
  val userIsSafe: Boolean = true,
  val isSosActive: Boolean = false,
  val showSosBroadcastDialog: Boolean = false,
  val showInteractiveBagDialog: Boolean = false,
  val showAddContactDialog: Boolean = false,
  val isFlashlightOn: Boolean = false,
  val isSirenOn: Boolean = false,
  val contactsList: List<EmergencyContact> = MockDisasterRepository.emergencyContacts,

  // --- Editable citizen profile + REAL device battery (replaces hardcoded 84%) ---
  val userProfile: UserProfile = UserProfile(),
  val showEditProfileDialog: Boolean = false,
  val batteryPercent: Int? = null,
  val isBatteryCharging: Boolean = false,
  val showSosConfirmDialog: Boolean = false,
  val showSituationReportDialog: Boolean = false,
  val isSubmittingReport: Boolean = false,
  val lastReportReceipt: ReportReceipt? = null,
  val weather: WeatherMetrics = WeatherMetrics(),
  val snackbarMessage: String? = null,

  // --- Location (REAL GPS preferred; labeled India fallback otherwise) ---
  val userLocation: GeoPoint = PilotRegionData.FALLBACK_USER_LOCATION,
  /** true -> location is the static India fallback; false -> REAL GPS fix. */
  val isUserLocationFallback: Boolean = true,

  // --- Intelligence results (recomputed on every location change) ---
  val hazardZones: List<HazardZone> = PilotRegionData.hazardZones,
  val safeZones: List<SafeZone> = PilotRegionData.safeZones,
  val personalRisk: PersonalRiskAssessment? = null,
  val recommendedAction: RecommendedAction? = null,
  val rankedShelters: List<SafeZoneEvaluation> = emptyList(),
  val relocationPlan: RelocationPlan? = null,

  // --- Routing ---
  val selectedSafeZone: SafeZone? = null,
  val selectedEvaluation: SafeZoneEvaluation? = null,
  val activeRoute: RouteResult? = null,
  val alternativeRoutes: List<RouteResult> = emptyList(),
  val isCalculatingRoute: Boolean = false,
  val travelMode: String = "foot", // "foot" or "driving"
  val isNavigatingLive: Boolean = false,
  val currentNavigationStepIndex: Int = 0,

  // --- Detail sheets ---
  val hazardDetailZone: HazardZone? = null,
  val safeZoneDetail: SafeZone? = null
) {

  // --- Derived broadcast labels (REAL battery/GPS/relays - no hardcoded 84%) ---
  /** Live device battery reading for SOS/report payloads - replaces the demo 84%. */
  val batteryLabel: String
    get() = batteryPercent?.let { percent ->
      "$percent%" + if (isBatteryCharging) " (Charging)" else " (Discharging)"
    } ?: "Reading device battery..."

  /** Real coordinates (GPS fix or pilot fallback) shown in the SOS dialogs. */
  val sosLocationLabel: String
    get() = String.format(
      "%.4f N, %.4f E (%s)",
      userLocation.lat,
      userLocation.lon,
      if (isUserLocationFallback) "FALLBACK" else "LIVE GPS"
    )

  /** Relay targets for a distress broadcast: NDRF 112 + kin contact count. */
  val priorityRelaysLabel: String
    get() = "NDRF 112 & " + contactsList.size + " Kin Contacts"
}

class VippattiViewModel(
  /** Emergency report gateway — NDRF pilot simulation by default, swappable. */
  private val reportService: EmergencyReportService = NdrfEmergencyReportService()
) : ViewModel() {

  private val _uiState = MutableStateFlow(VippattiUiState())
  val uiState: StateFlow<VippattiUiState> = _uiState.asStateFlow()

  private var audioJob: Job? = null
  private var sirenJob: Job? = null
  private var routingJob: Job? = null

  /** Location the current/last route was computed from — guards GPS re-routing. */
  private var lastRouteOrigin: GeoPoint? = null

  init {
    // Run the full intelligence pipeline once on the fallback location so the
    // map opens with risk, ranked shelters and a recommendation immediately.
    recomputeIntelligence(selectInitialShelter = true)
  }

  // ============================================================ INTELLIGENCE

  /**
   * The heart of the decision pipeline:
   * GPS/fallback location -> hazard analysis -> safe-zone discovery ->
   * capacity check -> ranked options -> personal risk -> recommended action ->
   * relocation plan -> evacuation route.
   */
  private fun recomputeIntelligence(selectInitialShelter: Boolean = false) {
    val state = _uiState.value
    val location = state.userLocation
    val hazards = state.hazardZones
    val zones = state.safeZones

    val risk = RiskAssessmentEngine.assess(
      location = location,
      hazards = hazards,
      provenanceNote = if (state.isUserLocationFallback) {
        "Location: India fallback (Painavu) • Hazards: simulated pilot data"
      } else {
        "Location: device GPS • Hazards: simulated pilot data"
      }
    )

    val ctx = SafeZoneEvaluator.RequestContext(
      origin = location,
      hazards = hazards,
      hasVulnerableMembers = state.userProfile.vulnerableCategoryIds.isNotEmpty(),
      needsMedicalSupport = state.userProfile.needsMedicalSupport
    )
    val ranked = SafeZoneEvaluator.ranked(zones, ctx)
    val action = ActionAdvisor.recommend(risk, ranked)
    val plan = RelocationPlanner.plan(
      risk,
      ranked,
      vulnerableCategoryIds = state.userProfile.vulnerableCategoryIds
    )

    _uiState.update {
      it.copy(
        personalRisk = risk,
        rankedShelters = ranked,
        recommendedAction = action,
        relocationPlan = plan
      )
    }

    if (selectInitialShelter) {
      val initial = ranked.firstOrNull()?.zone
      if (initial != null) {
        selectSafeZone(initial, autoRoute = true)
      }
    }
  }

  /**
   * Applies a REAL hardware GPS fix (reported by the osmdroid location overlay).
   * Real fixes replace the static India fallback and re-run every decision;
   * re-routing is movement-guarded so 1 Hz fixes never starve the OSRM request.
   */
  fun applyRealGpsFix(latitude: Double, longitude: Double) {
    val state = _uiState.value
    if (state.isUserLocationFallback || state.userLocation.lat != latitude || state.userLocation.lon != longitude) {
      _uiState.update {
        it.copy(
          userLocation = GeoPoint(latitude, longitude),
          isUserLocationFallback = false
        )
      }
      recomputeIntelligence()
      maybeRecalculateRouteForNewLocation()
    }
  }

  /**
   * Re-routes only when the user has moved at least [REROUTE_MIN_MOVEMENT_METERS]
   * from the origin of the displayed route. GPS fixes arrive ~1/sec with
   * sub-metre jitter — re-routing on every fix would keep cancelling the
   * in-flight OSRM request (route starvation: no corridor could ever finish
   * computing, so no polyline was ever drawn). A route the user explicitly
   * cleared is never resurrected by movement alone.
   */
  private fun maybeRecalculateRouteForNewLocation() {
    val state = _uiState.value
    if (state.selectedSafeZone == null) return
    if (state.activeRoute == null && lastRouteOrigin == null) return
    val routeOrigin = lastRouteOrigin
    val movedFarEnough = routeOrigin == null ||
      GeoMath.distanceMeters(routeOrigin, state.userLocation) >= REROUTE_MIN_MOVEMENT_METERS
    if (movedFarEnough) calculateRouteToSelectedZone()
  }

  // ================================================================ ROUTING

  /** Selects a safe zone (usually from the ranked list or a map tap). */
  fun selectSafeZone(zone: SafeZone, autoRoute: Boolean = true) {
    val evaluation = _uiState.value.rankedShelters.firstOrNull { it.zone.id == zone.id }
    _uiState.update {
      it.copy(
        selectedSafeZone = zone,
        selectedEvaluation = evaluation,
        safeZoneDetail = null,
        currentNavigationStepIndex = 0
      )
    }
    if (autoRoute) calculateRouteToSelectedZone()
  }

  fun calculateRouteToSelectedZone() {
    val zone = _uiState.value.selectedSafeZone ?: return
    routingJob?.cancel()
    lastRouteOrigin = _uiState.value.userLocation
    routingJob = viewModelScope.launch {
      _uiState.update { it.copy(isCalculatingRoute = true) }
      val result = OsrmRoutingService.calculateRoute(
        origin = _uiState.value.userLocation,
        destination = zone.point,
        mode = _uiState.value.travelMode,
        hazards = _uiState.value.hazardZones,
        destinationName = zone.name
      )
      _uiState.update {
        it.copy(
          isCalculatingRoute = false,
          activeRoute = result,
          currentNavigationStepIndex = 0
        )
      }
    }
  }

  /**
   * Computes alternative corridors to the selected shelter so the user can
   * compare safety vs distance.
   */
  fun loadAlternativeRoutes() {
    val zone = _uiState.value.selectedSafeZone ?: return
    routingJob?.cancel()
    lastRouteOrigin = _uiState.value.userLocation
    routingJob = viewModelScope.launch {
      _uiState.update { it.copy(isCalculatingRoute = true) }
      val alternatives = OsrmRoutingService.calculateAlternativeRoutes(
        origin = _uiState.value.userLocation,
        destination = zone.point,
        mode = _uiState.value.travelMode,
        hazards = _uiState.value.hazardZones,
        destinationName = zone.name,
        maxAlternatives = 2
      )
      _uiState.update {
        it.copy(
          isCalculatingRoute = false,
          activeRoute = alternatives.firstOrNull() ?: it.activeRoute,
          alternativeRoutes = alternatives
        )
      }
    }
  }

  /**
   * Clears the computed evacuation corridor (map "Clear Route" control).
   * Single source of truth: state resets here and the OSMDroid layer removes
   * its polyline because activeRoute becomes null — never a map-only removal
   * that would leave state and map disagreeing.
   */
  fun clearActiveRoute() {
    routingJob?.cancel()
    routingJob = null
    lastRouteOrigin = null
    _uiState.update {
      it.copy(
        activeRoute = null,
        alternativeRoutes = emptyList(),
        isCalculatingRoute = false,
        isNavigatingLive = false,
        currentNavigationStepIndex = 0,
        snackbarMessage = "Evacuation route cleared"
      )
    }
  }

  fun setTravelMode(mode: String) {
    if (_uiState.value.travelMode != mode) {
      _uiState.update { it.copy(travelMode = mode) }
      calculateRouteToSelectedZone()
    }
  }

  /**
   * One-tap "best safe zone" decision: selects the top-ranked feasible shelter
   * (NOT simply the nearest) and routes to it.
   */
  fun selectBestSafeZone() {
    val best = _uiState.value.rankedShelters.firstOrNull() ?: return
    selectSafeZone(best.zone, autoRoute = true)
    _uiState.update {
      it.copy(snackbarMessage = "Best safe zone selected: ${best.zone.name} — ${best.rankExplanation}")
    }
  }

  // =========================================================== NAVIGATION

  fun startEvacuationRoute() {
    val zone = _uiState.value.selectedSafeZone ?: return
    _uiState.update {
      it.copy(
        isNavigatingLive = true,
        currentTab = ScreenTab.RADAR_MAP,
        snackbarMessage = "Live guidance to ${zone.name} started"
      )
    }
    if (_uiState.value.activeRoute == null) {
      calculateRouteToSelectedZone()
    }
  }

  fun stopLiveNavigation() {
    _uiState.update {
      it.copy(
        isNavigatingLive = false,
        snackbarMessage = "Live evacuation guidance ended"
      )
    }
  }

  fun nextNavigationStep() {
    val currentRoute = _uiState.value.activeRoute ?: return
    val nextIndex = _uiState.value.currentNavigationStepIndex + 1
    if (nextIndex < currentRoute.steps.size) {
      _uiState.update { it.copy(currentNavigationStepIndex = nextIndex) }
    } else {
      _uiState.update {
        it.copy(
          isNavigatingLive = false,
          snackbarMessage = "You have arrived safely at ${_uiState.value.selectedSafeZone?.name ?: "the safe zone"}!"
        )
      }
    }
  }

  // ================================================================= TABS

  fun setTab(tab: ScreenTab) {
    _uiState.update { it.copy(currentTab = tab) }
  }

  fun toggleTheme() {
    _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
  }

  fun toggleOfflineCache(active: Boolean) {
    _uiState.update {
      it.copy(
        is100PercentOfflineCached = active,
        snackbarMessage = if (active) "Offline survival pack & OSM tiles cached" else "Switched to live streaming mode"
      )
    }
  }

  fun syncData() {
    viewModelScope.launch {
      _uiState.update { it.copy(isSyncing = true) }
      delay(1000)
      val timeNow = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
      _uiState.update {
        it.copy(
          isSyncing = false,
          lastSyncTime = "Today at $timeNow • Emergency SMS live",
          snackbarMessage = "Disaster intelligence, OSM tiles & radar grid refreshed"
        )
      }
    }
  }

  fun toggleAudioBulletin() {
    val willPlay = !_uiState.value.isAudioPlaying
    _uiState.update { it.copy(isAudioPlaying = willPlay) }

    audioJob?.cancel()
    if (willPlay) {
      audioJob = viewModelScope.launch {
        var seconds = 0
        while (_uiState.value.isAudioPlaying) {
          delay(1000)
          seconds++
          _uiState.update { it.copy(audioPlaybackSeconds = seconds) }
        }
      }
    }
  }

  fun setNewsCategory(category: String) {
    _uiState.update { it.copy(selectedNewsCategory = category) }
  }

  fun toggleGoBagItem(itemId: String) {
    _uiState.update { state ->
      val updated = state.goBagItems.map {
        if (it.id == itemId) it.copy(isChecked = !it.isChecked) else it
      }
      state.copy(goBagItems = updated)
    }
  }

  // ================================================== SAFETY / SOS / TOOLS

  fun setUserSafety(isSafe: Boolean) {
    if (!isSafe) {
      // NEED ASSISTANCE arms a distress broadcast - an explicit "Are you
      // sure?" confirmation is required before anything is transmitted.
      _uiState.update { it.copy(showSosConfirmDialog = true) }
      return
    }
    _uiState.update {
      it.copy(
        userIsSafe = true,
        snackbarMessage = "Status updated: Marked as SAFE on SARANA network"
      )
    }
  }

  /**
   * Any SOS entry point (radar SOS icon, hero broadcast button, NEED
   * ASSISTANCE switch) first opens the "Are you sure?" confirmation gate -
   * nothing is broadcast until the user explicitly confirms.
   */
  fun triggerSosBroadcast() {
    _uiState.update {
      it.copy(showSosConfirmDialog = true)
    }
  }

  fun dismissSosDialog() {
    _uiState.update { it.copy(showSosBroadcastDialog = false) }
  }

  fun cancelSosBroadcast() {
    _uiState.update {
      it.copy(
        showSosBroadcastDialog = false,
        isSosActive = false,
        snackbarMessage = "SOS emergency broadcast canceled"
      )
    }
  }

  fun dismissSosConfirmDialog() {
    _uiState.update { it.copy(showSosConfirmDialog = false) }
  }

  /**
   * Confirms the "Are you sure?" gate: arms the live SOS broadcast and files
   * the corresponding NDRF distress report through EmergencyReportService.
   */
  fun confirmSosBroadcast() {
    _uiState.update {
      it.copy(
        showSosConfirmDialog = false,
        showSosBroadcastDialog = true,
        isSosActive = true,
        userIsSafe = false
      )
    }
    val profile = _uiState.value.userProfile
    submitEmergencyReport(
      ReportKind.SOS_BROADCAST,
      message = "NEED ASSISTANCE - SOS distress broadcast from ${profile.fullName}" +
        (if (profile.medicalTag.isNotBlank()) " | Medical tag: ${profile.medicalTag}" else "")
    )
  }

  // =========================== PROFILE / BATTERY ===========================

  fun openEditProfileDialog() {
    _uiState.update { it.copy(showEditProfileDialog = true) }
  }

  fun closeEditProfileDialog() {
    _uiState.update { it.copy(showEditProfileDialog = false) }
  }

  /**
   * Saves the edited citizen profile. Vulnerable categories and the medical
   * flag change shelter ranking and the relocation priority band, so the
   * intelligence pipeline is re-run immediately.
   */
  fun updateUserProfile(profile: UserProfile) {
    _uiState.update {
      it.copy(
        userProfile = profile,
        showEditProfileDialog = false,
        snackbarMessage = "Profile saved: ${profile.fullName} | ${profile.bloodGroupLabel} | ${profile.dependentsLabel}"
      )
    }
    recomputeIntelligence()
  }

  /** Real device battery reading pushed by the BatteryManager receiver in MainActivity. */
  fun onBatteryChanged(percent: Int, isCharging: Boolean) {
    _uiState.update { it.copy(batteryPercent = percent, isBatteryCharging = isCharging) }
  }

  fun toggleFlashlight() {
    _uiState.update {
      val next = !it.isFlashlightOn
      it.copy(
        isFlashlightOn = next,
        snackbarMessage = if (next) "Emergency Flashlight turned ON" else "Flashlight turned OFF"
      )
    }
  }

  fun toggleSiren() {
    val willActivate = !_uiState.value.isSirenOn
    _uiState.update {
      it.copy(
        isSirenOn = willActivate,
        snackbarMessage = if (willActivate) "HIGH-DECIBEL SOS SIREN ACTIVE" else "SOS Siren deactivated"
      )
    }
    sirenJob?.cancel()
    if (willActivate) {
      sirenJob = viewModelScope.launch {
        delay(60000)
        _uiState.update { it.copy(isSirenOn = false) }
      }
    }
  }

  // ======================= SITUATION REPORTS (NDRF) ========================

  /** Opens the "Report My Situation" voice/form/photo reporter. */
  fun openSituationReportDialog() {
    _uiState.update { it.copy(showSituationReportDialog = true) }
  }

  fun closeSituationReportDialog() {
    _uiState.update { it.copy(showSituationReportDialog = false) }
  }

  /**
   * Submits the citizen's situation report (voice/typed description +
   * optional photo evidence) through the EmergencyReportService to the
   * NDRF ward dispatcher.
   */
  fun submitSituationReport(message: String, photoUri: String?) {
    if (message.isBlank()) {
      _uiState.update {
        it.copy(snackbarMessage = "Describe your situation (type or dictate) before reporting")
      }
      return
    }
    _uiState.update {
      it.copy(showSituationReportDialog = false, isSubmittingReport = true)
    }
    submitEmergencyReport(ReportKind.SITUATION_REPORT, message.trim(), photoUri)
  }

  /** Shared NDRF funnel for SOS broadcasts and situation reports. */
  private fun submitEmergencyReport(kind: ReportKind, message: String, photoUri: String? = null) {
    val snapshot = _uiState.value
    val profile = snapshot.userProfile
    viewModelScope.launch {
      val receipt = try {
        reportService.submit(
          EmergencyReport(
            kind = kind,
            reporterId = profile.citizenId,
            reporterName = profile.fullName,
            message = message,
            photoUri = photoUri,
            location = snapshot.userLocation,
            batteryPercent = snapshot.batteryPercent,
            isCharging = snapshot.isBatteryCharging,
            medicalTag = profile.medicalTag
          )
        )
      } catch (e: Exception) {
        ReportReceipt(
          reportId = "NDRF-ERR",
          accepted = false,
          relayChannel = NdrfEmergencyReportService.RELAY_CHANNEL,
          etaMinutes = null,
          note = "Report failed: ${e.message ?: "dispatcher unreachable"}"
        )
      }
      _uiState.update {
        it.copy(
          isSubmittingReport = false,
          lastReportReceipt = receipt,
          snackbarMessage = receipt.note
        )
      }
    }
  }

  // ============================================================= DIALOGS

  fun openInteractiveBagDialog() {
    _uiState.update { it.copy(showInteractiveBagDialog = true) }
  }

  fun closeInteractiveBagDialog() {
    _uiState.update { it.copy(showInteractiveBagDialog = false) }
  }

  fun openAddContactDialog() {
    _uiState.update { it.copy(showAddContactDialog = true) }
  }

  fun closeAddContactDialog() {
    _uiState.update { it.copy(showAddContactDialog = false) }
  }

  fun addContact(name: String, relation: String, phone: String, location: String) {
    val newContact = EmergencyContact(
      id = "c-${System.currentTimeMillis()}",
      name = name,
      role = relation,
      phone = phone,
      locationNote = location,
      initials = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase(),
      colorHex = 0xFF4F46E5
    )
    _uiState.update {
      it.copy(
        contactsList = it.contactsList + newContact,
        showAddContactDialog = false,
        snackbarMessage = "Added $name to emergency kin network"
      )
    }
  }

  // ========================================================= DETAIL SHEETS

  /** Opens the hazard detail sheet from a map-zone tap. */
  fun openHazardDetail(zone: HazardZone) {
    _uiState.update { it.copy(hazardDetailZone = zone) }
  }

  fun closeHazardDetail() {
    _uiState.update { it.copy(hazardDetailZone = null) }
  }

  /** Opens the safe-zone detail sheet from a map-zone tap. */
  fun openSafeZoneDetail(zone: SafeZone) {
    _uiState.update { it.copy(safeZoneDetail = zone) }
  }

  fun closeSafeZoneDetail() {
    _uiState.update { it.copy(safeZoneDetail = null) }
  }

  fun clearSnackbar() {
    _uiState.update { it.copy(snackbarMessage = null) }
  }
}
