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
import com.example.BuildConfig
import com.example.data.news.GNewsServiceImpl
import com.example.data.news.MemoryNewsCache
import com.example.data.news.NewsArticle
import com.example.data.news.NewsCache
import com.example.data.news.NewsError
import com.example.data.news.NewsFeed
import com.example.data.news.NewsRepository
import com.example.data.news.NewsTtsBulletin
import com.example.data.model.GeoMath
import com.example.data.model.HazardSeverity
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
import com.example.data.disaster.DisasterCache
import com.example.data.disaster.DisasterDataRepository
import com.example.data.disaster.DisasterEvent
import com.example.data.disaster.DisasterLayer
import com.example.data.disaster.DisasterSource
import com.example.data.disaster.FirmsFireProvider
import com.example.data.disaster.ImdCapProvider
import com.example.data.disaster.IncidentCategory
import com.example.data.disaster.IncidentReport
import com.example.data.disaster.IndiaGeo
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.ProviderState
import com.example.data.disaster.UsgsEarthquakeProvider
import com.example.data.disaster.defaultSeverity
import com.example.data.disaster.dedupeBySourceEventId
import com.example.data.disaster.toHazardZones
import com.example.data.disaster.isValid
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

/** Cap on locally-held citizen reports (device memory guard). */
private const val MAX_INCIDENT_REPORTS = 50

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
  val lastSyncTime: String = "Not synced yet — tap Sync to fetch live GNews disaster news",
  val isSyncing: Boolean = false,
  val isAudioPlaying: Boolean = false,
  val audioPlaybackSeconds: Int = 0,
  val selectedNewsCategory: String = "All",
  // --- REAL GNews disaster-news pipeline (articles are never fabricated) ---
  val newsArticles: List<NewsArticle> = emptyList(),
  val newsHero: NewsArticle? = null,
  val isNewsFromCache: Boolean = false,
  val newsLastFetchedAtMillis: Long? = null,
  val newsEverLoaded: Boolean = false,
  val newsError: NewsError? = null,
  /** Spoken-bulletin text — composed from real state, consumed by real TTS. */
  val audioBulletinText: String = "",
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

  // --- Location (REAL GPS preferred; India-centre view until a fix arrives) ---
  val userLocation: GeoPoint = GeoPoint(
    com.example.data.disaster.IndiaGeo.CENTER_LAT,
    com.example.data.disaster.IndiaGeo.CENTER_LON
  ),
  /**
   * true -> NO GPS fix yet; the user location is just the India map centre
   * (NEVER a district-level fallback). false -> real device GPS fix.
   */
  val isUserLocationFallback: Boolean = true,

  // --- REAL India-wide disaster pipeline (USGS + FIRMS + IMD CAP) ---
  /** Live, provider-sourced events (normalized, validated, deduped). */
  val disasterEvents: List<DisasterEvent> = emptyList(),
  /** Per-source freshness/status (Live / Recent / Cached / Unavailable). */
  val providerStates: List<ProviderState> = emptyList(),
  /** True while a disaster-data sync is in flight. */
  val isDisasterSyncing: Boolean = false,
  /** Aggregated label: LIVE when any provider is live, else cached/unavailable. */
  val isDisasterDataLive: Boolean = false,
  /** Latest sync time across providers (for "Last updated" display). */
  val disasterLastSyncMillis: Long? = null,
  /** Map layer toggles (user-controlled; zoom rules applied at render time). */
  val enabledLayers: Set<DisasterLayer> = setOf(
    DisasterLayer.OFFICIAL_ALERTS, DisasterLayer.EARTHQUAKES,
    DisasterLayer.USER_REPORTS, DisasterLayer.SAFE_ZONES,
    DisasterLayer.EVACUATION_ROUTE, DisasterLayer.MY_LOCATION
  ),
  /** Explicit DEMO MODE — clearly-labeled pilot/simulated data. Off by default. */
  val isDemoMode: Boolean = false,
  /** Citizen-submitted incident reports (unverified, TTL'd). */
  val userIncidentReports: List<IncidentReport> = emptyList(),
  /** Selected disaster event for the tap detail panel. */
  val disasterEventDetail: DisasterEvent? = null,
  /** Dialog for the "Add a Report" incident flow. */
  val showIncidentReportDialog: Boolean = false,

  // --- Intelligence results (recomputed on every location change) ---
  val hazardZones: List<HazardZone> = emptyList(),
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

  /** Real coordinates (GPS fix or India-centre view) shown in the SOS dialogs. */
  val sosLocationLabel: String
    get() = String.format(
      "%.4f N, %.4f E (%s)",
      userLocation.lat,
      userLocation.lon,
      if (isUserLocationFallback) "NO GPS FIX" else "LIVE GPS"
    )

  /** Relay targets for a distress broadcast: NDRF 112 + kin contact count. */
  val priorityRelaysLabel: String
    get() = "NDRF 112 & " + contactsList.size + " Kin Contacts"
}

class VippattiViewModel(
  /** Emergency report gateway — NDRF pilot simulation by default, swappable. */
  private val reportService: EmergencyReportService = NdrfEmergencyReportService(),
  /**
   * Real GNews disaster-news cache — file-backed in production (MainActivity
   * injects NewsFileCache so cached articles survive process restarts),
   * in-memory by default (unit tests).
   */
  newsCache: NewsCache = MemoryNewsCache(),
  /**
   * REAL disaster-data cache — file-backed in production (MainActivity
   * injects DisasterFileCache so provider shards survive process restarts),
   * in-memory by default (unit tests).
   */
  disasterCache: DisasterCache = MemoryDisasterCache(),
  /**
   * REAL India-wide disaster data repository. Default: live USGS (keyless),
   * NASA FIRMS (free MAP_KEY via app/.env) and IMD official CAP alerts
   * (keyless). Tests inject a repository with fake providers.
   */
  private val disasterRepository: DisasterDataRepository = DisasterDataRepository(
    providers = listOf(
      UsgsEarthquakeProvider(),
      ImdCapProvider(),
      FirmsFireProvider(mapKeyProvider = { BuildConfig.FIRMS_MAP_KEY })
    ),
    cache = disasterCache
  )
) : ViewModel() {

  /** Real GNews disaster-news pipeline (live API + offline cache). */
  private val newsRepository: NewsRepository = NewsRepository(
    service = GNewsServiceImpl(),
    cache = newsCache,
    apiKeyProvider = { BuildConfig.GNEWS_API_KEY }
  )

  private val _uiState = MutableStateFlow(VippattiUiState())
  val uiState: StateFlow<VippattiUiState> = _uiState.asStateFlow()

  private var audioJob: Job? = null
  private var sirenJob: Job? = null
  private var routingJob: Job? = null
  private var newsJob: Job? = null
  private var disasterJob: Job? = null

  /** Location the current/last route was computed from — guards GPS re-routing. */
  private var lastRouteOrigin: GeoPoint? = null

  init {
    // Cold start: run the intelligence pipeline once on the India-centre view
    // so risk is assessed (GREEN without GPS — no fake hazards), then pull
    // the REAL India-wide disaster feeds (cache-first when offline).
    recomputeIntelligence(selectInitialShelter = false)
    disasterJob = viewModelScope.launch {
      val cached = disasterRepository.loadCachedOnly()
      if (cached != null) {
        applyDisasterFeed(cached)
      } else {
        applyDisasterFeed(disasterRepository.refresh())
      }
    }
    // Cold start of the REAL GNews pipeline: a fresh cache (< 30 min) serves
    // instantly (offline survival + quota protection); otherwise it fetches.
    newsJob = viewModelScope.launch { applyNewsFeed(newsRepository.ensureLoaded()) }
  }

  // ============================================================ INTELLIGENCE

  /**
   * The heart of the decision pipeline:
   * GPS/India-centre location -> hazard analysis (REAL live events + user
   * reports + explicitly-labeled demo data) -> safe-zone discovery ->
   * capacity check -> ranked options -> personal risk -> recommended action ->
   * relocation plan -> evacuation route.
   */
  private fun recomputeIntelligence(selectInitialShelter: Boolean = false) {
    val state = _uiState.value
    val location = state.userLocation
    val now = System.currentTimeMillis()

    // Hazard picture assembly — LIVE data first, never silently mixed:
    //   1. REAL provider events (normalized to HazardZone for every engine).
    //   2. Unverified user incident reports (TTL-expired ones drop out).
    //   3. DEMO MODE pilot zones — ONLY when the user explicitly enabled it.
    val liveZones = toHazardZones(
      state.disasterEvents.filter { it.isValid(now) }
    )
    val reportZones = toHazardZones(
      state.userIncidentReports
        .map { it.toDisasterEvent(now) }
        .filter { it.isValid(now) }
    )
    val demoZones = if (state.isDemoMode) PilotRegionData.hazardZones else emptyList()
    val hazards = (liveZones + reportZones + demoZones).distinctBy { it.id }

    val zones = state.safeZones
    // Safe zones exist ONLY in the pilot coverage area; outside it, ranking
    // simply yields no feasible shelter (honest empty state), never a fake
    // nationwide shelter list.
    val zonesInScope = if (state.isDemoMode || IndiaGeo.isWithinPilotCoverage(location)) {
      zones
    } else {
      zones.filter { IndiaGeo.isWithinPilotCoverage(it.point) }
    }

    val risk = RiskAssessmentEngine.assess(
      location = location,
      hazards = hazards,
      provenanceNote = when {
        state.isDemoMode ->
          "Location: ${if (state.isUserLocationFallback) "India centre (no GPS)" else "device GPS"} • Demo mode: labeled pilot hazards shown"
        state.isUserLocationFallback ->
          "Location: India centre (no GPS yet) • Hazards: live provider feeds"
        else ->
          "Location: device GPS • Hazards: live provider feeds + user reports"
      }
    )

    val ctx = SafeZoneEvaluator.RequestContext(
      origin = location,
      hazards = hazards,
      hasVulnerableMembers = state.userProfile.vulnerableCategoryIds.isNotEmpty(),
      needsMedicalSupport = state.userProfile.needsMedicalSupport
    )
    val ranked = SafeZoneEvaluator.ranked(zonesInScope, ctx)
    val action = ActionAdvisor.recommend(risk, ranked)
    val plan = RelocationPlanner.plan(
      risk,
      ranked,
      vulnerableCategoryIds = state.userProfile.vulnerableCategoryIds
    )

    _uiState.update {
      it.copy(
        personalRisk = risk,
        hazardZones = hazards,
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
   * Real fixes replace the India-centre placeholder and re-run every decision;
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

  // ====================================================== DISASTER PIPELINE

  /**
   * REAL disaster-data refresh: queries every live provider, updates the
   * cache, and re-runs the intelligence pipeline against the fresh events.
   */
  fun syncDisasterData() {
    if (_uiState.value.isDisasterSyncing) return
    disasterJob?.cancel()
    disasterJob = viewModelScope.launch {
      _uiState.update { it.copy(isDisasterSyncing = true) }
      val feed = disasterRepository.refresh()
      applyDisasterFeed(feed)
      _uiState.update { it.copy(isDisasterSyncing = false) }
    }
  }

  /** Publishes a disaster feed into state with honest per-source labels. */
  private fun applyDisasterFeed(feed: com.example.data.disaster.DisasterFeed) {
    val now = System.currentTimeMillis()
    _uiState.update {
      it.copy(
        disasterEvents = feed.events.filter { e -> e.isValid(now) },
        providerStates = feed.providerStates,
        isDisasterDataLive = feed.isAnyLive,
        disasterLastSyncMillis = feed.providerStates
          .mapNotNull { s -> s.fetchedAtMillis.takeIf { t -> t > 0 } }
          .maxOrNull()
      )
    }
    recomputeIntelligence()
  }

  /** Toggles one map layer (map layer panel). */
  fun toggleLayer(layer: DisasterLayer) {
    _uiState.update {
      val next = it.enabledLayers.toMutableSet()
      if (layer in next) next.remove(layer) else next.add(layer)
      it.copy(enabledLayers = next)
    }
  }

  /**
   * Explicit DEMO MODE toggle. ON -> the labeled Idukki pilot dataset is
   * shown for demonstration and every demo hazard stays provenance-labeled.
   * OFF -> only REAL provider data and user reports remain.
   */
  fun setDemoMode(enabled: Boolean) {
    _uiState.update {
      it.copy(
        isDemoMode = enabled,
        snackbarMessage = if (enabled) {
          "Demo mode ON — labeled pilot data for demonstration. Live provider data continues in parallel."
        } else {
          "Demo mode OFF — showing live provider data and user reports only."
        }
      )
    }
    recomputeIntelligence()
  }

  // ==================================================== USER INCIDENT REPORTS

  /** Opens the "Add a Report" incident dialog. */
  fun openIncidentReportDialog() {
    _uiState.update { it.copy(showIncidentReportDialog = true) }
  }

  fun closeIncidentReportDialog() {
    _uiState.update { it.copy(showIncidentReportDialog = false) }
  }

  /**
   * Submits a citizen incident report: stored locally, TTL-expired after
   * [com.example.data.disaster.INCIDENT_TTL_MILLIS], always displayed as an
   * UNVERIFIED user report — never as confirmed fact.
   */
  fun submitIncidentReport(
    category: IncidentCategory,
    severityLabel: String,
    description: String
  ) {
    val state = _uiState.value
    if (state.isUserLocationFallback) {
      _uiState.update {
        it.copy(
          showIncidentReportDialog = false,
          snackbarMessage = "Location unavailable — enable device GPS before reporting an incident."
        )
      }
      return
    }
    val severity = HazardSeverity.entries.firstOrNull { it.label == severityLabel }
      ?: category.defaultSeverity()
    val report = IncidentReport(
      id = "user-${System.currentTimeMillis()}",
      category = category,
      description = description.trim(),
      location = state.userLocation,
      reportedAtMillis = System.currentTimeMillis(),
      severity = severity,
      reporterName = state.userProfile.fullName
    )
    _uiState.update {
      it.copy(
        showIncidentReportDialog = false,
        userIncidentReports = (it.userIncidentReports + report).takeLast(MAX_INCIDENT_REPORTS)
      )
    }
    recomputeIntelligence()
    _uiState.update {
      it.copy(snackbarMessage = "Report added — unverified user report shown on the map for others to verify.")
    }
  }

  /** Opens the tap-detail panel for a disaster event. */
  fun openDisasterEventDetail(event: DisasterEvent) {
    _uiState.update { it.copy(disasterEventDetail = event) }
  }

  fun closeDisasterEventDetail() {
    _uiState.update { it.copy(disasterEventDetail = null) }
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

  /**
   * REAL refresh: pulls live disaster news from GNews (all three scopes,
   * quota-aware) AND re-syncs the India-wide disaster providers, then
   * re-labels the sync banner from the actual results.
   */
  fun syncData() {
    syncDisasterData()
    newsJob?.cancel()
    newsJob = viewModelScope.launch {
      _uiState.update { it.copy(isSyncing = true) }
      val feed = newsRepository.refresh()
      applyNewsFeed(feed)
      _uiState.update { state ->
        state.copy(
          isSyncing = false,
          snackbarMessage = when {
            feed.articles.isNotEmpty() && feed.error == null ->
              "Disaster intelligence refreshed — ${feed.articles.size} live GNews articles"
            feed.error != null -> feed.error.userMessage
            else -> "No GNews articles matched the pilot queries — try again later"
          }
        )
      }
    }
  }

  /** Publishes a real GNews feed into state with an honest sync label. */
  private fun applyNewsFeed(feed: NewsFeed) {
    val fetchedLabel = feed.lastFetchedAtMillis?.let { millis ->
      SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(millis))
    }
    _uiState.update {
      it.copy(
        newsArticles = feed.articles,
        newsHero = feed.hero,
        isNewsFromCache = feed.isFromCache,
        newsLastFetchedAtMillis = feed.lastFetchedAtMillis,
        newsEverLoaded = feed.articles.isNotEmpty(),
        newsError = feed.error,
        lastSyncTime = when {
          feed.articles.isNotEmpty() && feed.isFromCache ->
            "OFFLINE CACHE • ${feed.articles.size} articles • fetched $fetchedLabel"
          feed.articles.isNotEmpty() ->
            "LIVE SYNC • ${feed.articles.size} articles • $fetchedLabel"
          feed.error != null -> feed.error.userMessage
          else -> "No disaster news cached — tap Sync to fetch live articles"
        }
      )
    }
  }

  /**
   * Arms the REAL spoken bulletin: state carries the composed text and the
   * MainActivity TextToSpeech engine speaks it; the on-screen ticker runs
   * until the engine reports completion (or unavailability).
   */
  fun toggleAudioBulletin() {
    val willPlay = !_uiState.value.isAudioPlaying
    _uiState.update {
      it.copy(
        isAudioPlaying = willPlay,
        audioBulletinText = if (willPlay) buildAudioBulletin() else "",
        audioPlaybackSeconds = 0
      )
    }

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

  /** Bulletin text built ONLY from real current state — nothing invented. */
  private fun buildAudioBulletin(): String {
    val state = _uiState.value
    return NewsTtsBulletin.compose(
      riskLevelLabel = state.personalRisk?.level?.label ?: "not yet assessed",
      recommendedActionTitle = state.recommendedAction?.title,
      actionExplanation = state.recommendedAction?.explanation,
      articles = state.newsArticles,
      nowMillis = System.currentTimeMillis(),
      isFromCache = state.isNewsFromCache
    )
  }

  /** TextToSpeech finished (or failed) — stop the ticker honestly. */
  fun onTtsBulletinFinished() {
    audioJob?.cancel()
    _uiState.update { it.copy(isAudioPlaying = false, audioPlaybackSeconds = 0) }
  }

  /** No TextToSpeech engine on this device — say so instead of faking playback. */
  fun onTtsUnavailable() {
    audioJob?.cancel()
    _uiState.update {
      it.copy(
        isAudioPlaying = false,
        audioPlaybackSeconds = 0,
        snackbarMessage = "Text-to-speech unavailable on this device — bulletin cannot be spoken"
      )
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
