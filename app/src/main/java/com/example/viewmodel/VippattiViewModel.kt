package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.disaster.EmergencyContact
import com.example.data.disaster.GoBagItem
import com.example.data.disaster.MockDisasterRepository
import com.example.data.disaster.PilotRegionData
import com.example.data.model.UserProfile
import com.example.data.disaster.WeatherMetrics
import com.example.data.reports.EmergencyReport
import com.example.data.reports.EmergencyReportService
import com.example.data.reports.LocalEmergencyReportService
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
import com.example.data.weather.OpenMeteoWeatherService
import com.example.data.model.GeoMath
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint
import com.example.data.routing.LiveRouteCache
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
import com.example.data.disaster.providers.FirmsFireProvider
import com.example.data.disaster.providers.ImdCapProvider
import com.example.data.disaster.IncidentCategory
import com.example.data.disaster.IncidentReport
import com.example.data.disaster.IndiaGeo
import com.example.data.disaster.MemoryDisasterCache
import com.example.data.disaster.ProviderState
import com.example.data.disaster.providers.UsgsEarthquakeProvider
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

/** Siren auto-stop window, surfaced to the user as a visible countdown. */
const val SIREN_MAX_SECONDS = 60

/** Reason string returned when the CAMERA permission is required for the torch. */
const val TORCH_REASON_PERMISSION = "Camera permission is required to switch the light on."

/**
 * Weather refresh guard. A 1 Hz GPS stream used to trigger one Open-Meteo HTTP
 * request per fix; the reading is now reused for [WEATHER_MIN_REFRESH_MS] and
 * only re-fetched after meaningful movement or a manual sync.
 */
private const val WEATHER_MIN_REFRESH_MS = 10L * 60L * 1000L
private const val WEATHER_MIN_MOVEMENT_METERS = 2_000.0


class VippattiViewModel(
  /** Local emergency-report recorder (no backend relay exists in this build). */
  private val reportService: EmergencyReportService = LocalEmergencyReportService(),
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
   * Directory of the osmdroid tile cache — injected by MainActivity so the
   * offline map-cache size shown on the Profile screen is a REAL disk
   * measurement (no fabricated "48 MB / 64 MB" values).
   */
  private val tileCacheDirProvider: () -> java.io.File? = { null },
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
  ),
  /** News pipeline override — tests inject a failing/fake service (no real network). */
  private val newsRepositoryOverride: NewsRepository? = null,
  /**
   * Weather fetcher — production reads live Open-Meteo; tests inject a fake so
   * unit tests never perform real HTTP.
   */
  private val weatherFetcher: suspend (GeoPoint) -> WeatherMetrics? =
    OpenMeteoWeatherService::fetchNow
) : ViewModel() {

  /** Real GNews disaster-news pipeline (live API + offline cache). */
  private val newsRepository: NewsRepository = newsRepositoryOverride
    ?: NewsRepository(
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
  private var weatherJob: Job? = null

  /** Location the current/last route was computed from — guards GPS re-routing. */
  private var lastRouteOrigin: GeoPoint? = null

  /** Weather reading guard: TTL + movement anchor (see [refreshWeather]). */
  private var weatherFetchedAtMillis = 0L
  private var weatherAnchor: GeoPoint? = null

  /** Live road routes by destination/mode/origin-grid/hazards — instant exact roads on repeat views. */
  private val liveRouteCache = LiveRouteCache()

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
    // Cold start of LIVE weather (Open-Meteo, keyless) for the radar bar.
    refreshWeather()
  }

  /**
   * Pulls LIVE temperature / rainfall / wind + 3-hour trend for the current
   * map location. Offline or API failure keeps the previous reading (or the
   * honest empty state on first run) — weather is never invented.
   */
  fun refreshWeather(force: Boolean = false) {
    // PERFORMANCE FIX: this used to fire one HTTP request per GPS fix (1 Hz).
    // A reading is now reused until the TTL expires or the user has moved
    // meaningfully; a manual Sync still forces a fresh reading.
    val anchor = _uiState.value.userLocation
    val stillFresh = System.currentTimeMillis() - weatherFetchedAtMillis < WEATHER_MIN_REFRESH_MS
    val movedFarEnough = weatherAnchor?.let { previous ->
      GeoMath.distanceMeters(previous, anchor) >= WEATHER_MIN_MOVEMENT_METERS
    } ?: true
    if (!force && stillFresh && !movedFarEnough) return

    weatherJob?.cancel()
    weatherJob = viewModelScope.launch {
      val live = weatherFetcher(anchor)
      if (live != null) {
        weatherFetchedAtMillis = System.currentTimeMillis()
        weatherAnchor = anchor
        _uiState.update { it.copy(weather = live, isWeatherLive = true) }
      } else if (_uiState.value.weather == WeatherMetrics()) {
        _uiState.update { it.copy(isWeatherLive = false) }
      }
    }
  }

  // ============================================================ INTELLIGENCE

  /**
   * The heart of the decision pipeline:
   * GPS/India-centre location -> hazard analysis (REAL live events + user
   * reports + explicitly-labeled mock data) -> safe-zone discovery ->
   * capacity check -> ranked options -> personal risk -> recommended action ->
   * relocation plan -> evacuation route.
   */
  private fun recomputeIntelligence(selectInitialShelter: Boolean = false) {
    val state = _uiState.value
    val location = state.userLocation
    val now = System.currentTimeMillis()

    // Hazard picture assembly.
    // LIVE provider events and citizen reports are ALWAYS included: the
    // "simulated demo" switch must never hide real hazards. Previously it emptied
    // the whole picture, which silently produced a GREEN risk verdict and empty
    // routing anywhere in India whenever demo data was switched off.
    val liveZones = toHazardZones(state.disasterEvents.filter { it.isValid(now) })
    val reportZones = toHazardZones(
      state.userIncidentReports
        .map { it.toDisasterEvent(now) }
        .filter { it.isValid(now) }
    )
    val mockZones = if (state.isMockDataVisible) {
      // India-only guard so a record outside IndiaGeo never reaches the map.
      PilotRegionData.hazardZones.filter { IndiaGeo.contains(it.center) }
    } else {
      emptyList()
    }
    val hazards = (liveZones + reportZones + mockZones).distinctBy { it.id }

    // The shelter network is entirely SIMULATED today (there is no shelter
    // registry backend), so it is offered only while the demo switch is on.
    // There is no live shelter source to fall back to, and inventing one would
    // be fabrication.
    val zonesInScope = if (state.isMockDataVisible) {
      state.safeZones.filter { IndiaGeo.contains(it.point) }
    } else {
      emptyList()
    }

    val risk = RiskAssessmentEngine.assess(
      location = location,
      hazards = hazards,
      provenanceNote = when {
        state.isMockDataVisible ->
          "Location: ${if (state.isUserLocationFallback) "India centre (no GPS)" else "device GPS"} • " +
            "Hazards: live provider feeds + citizen reports + labelled SIMULATED demo zones • " +
            "Shelters: SIMULATED demo records (no shelter registry connected)"
        state.isUserLocationFallback ->
          "Location: India centre (no GPS yet) • Hazards: live provider feeds + citizen reports only"
        else ->
          "Location: device GPS • Hazards: live provider feeds + citizen reports only"
      }
    )

    val ctx = SafeZoneEvaluator.RequestContext(
      origin = location,
      hazards = hazards,
      hasVulnerableMembers = state.userProfile.vulnerableCategoryIds.isNotEmpty(),
      needsMedicalSupport = state.userProfile.needsMedicalSupport
    )
    // Evaluate EVERY candidate: feasible shelters are ranked; rejected ones
    // carry their rejection reason so the UI can never present a full or
    // hazard-trapped shelter as an eligible destination.
    val evaluated = SafeZoneEvaluator.evaluateAll(zonesInScope, ctx)
    val ranked = evaluated.filter { it.isFeasible }.sortedByDescending { it.score }
    val action = ActionAdvisor.recommend(risk, ranked)
    val plan = RelocationPlanner.plan(
      risk,
      ranked,
      vulnerableCategoryIds = state.userProfile.vulnerableCategoryIds
    )

    _uiState.update {
      val selectedStillVisible = it.selectedSafeZone?.let { sel ->
        zonesInScope.any { z -> z.id == sel.id }
      } ?: true
      // Hiding demo data invalidates any simulated destination + its corridor —
      // the map must never keep routing to a zone that just disappeared.
      it.copy(
        personalRisk = risk,
        hazardZones = hazards,
        evaluatedShelters = evaluated,
        rankedShelters = ranked,
        recommendedAction = action,
        relocationPlan = plan,
        selectedSafeZone = if (selectedStillVisible) it.selectedSafeZone else null,
        selectedEvaluation = if (selectedStillVisible) it.selectedEvaluation else null,
        activeRoute = if (selectedStillVisible) it.activeRoute else null,
        alternativeRoutes = if (selectedStillVisible) it.alternativeRoutes else emptyList(),
        routeStatus = if (selectedStillVisible) it.routeStatus else RouteStatus.IDLE,
        routeStatusMessage = if (selectedStillVisible) it.routeStatusMessage else null,
        isNavigatingLive = if (selectedStillVisible) it.isNavigatingLive else false
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
      refreshWeather()
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
   * Simulated-demo-data switch. ON -> the labelled SIMULATED India network is
   * shown; OFF -> the simulated network disappears while LIVE provider events
   * and citizen reports keep reaching the map and every engine.
   * Kept in sync with the radar "SIMULATED DEMO" toggle (same dataset).
   */
  fun setMockMode(enabled: Boolean) {
    _uiState.update {
      it.copy(
        isMockMode = enabled,
        isMockDataVisible = enabled,
        snackbarMessage = if (enabled) {
          "Simulated demo ON — labelled India zones shown; live data unchanged"
        } else {
          "Simulated demo OFF — live data still shown"
        }
      )
    }
    recomputeIntelligence()
  }

  /**
   * Radar "SIMULATED DEMO" toggle: ON adds the labelled mock danger + safe
   * zones; OFF removes ONLY the simulated network — live provider events,
   * citizen reports, risk assessment and routing keep working.
   * Single source of truth for simulated-data visibility.
   */
  fun toggleMockData() {
    val next = !_uiState.value.isMockDataVisible
    _uiState.update {
      it.copy(
        isMockDataVisible = next,
        isMockMode = next,
        snackbarMessage = if (next) {
          "Simulated demo ON — labelled India zones shown; live data unchanged"
        } else {
          "Simulated demo OFF — live data still shown"
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
      it.copy(snackbarMessage = "Report added: ${category.label} — UNVERIFIED user report shown on the map for others to verify.")
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

  /**
   * Instant corridor first, live road route as an upgrade:
   *  1. A cached live road route (same shelter/mode/area/hazards) paints the
   *     EXACT road pathway immediately with no straight-line flash at all.
   *  2. Otherwise the offline hazard-skirting corridor draws instantly as a
   *     DASHED preview (never mistaken for surveyed roads).
   *  3. The live OSRM road pathway swaps in when it arrives — the badge
   *     flips OFFLINE EST. -> OSRM VALIDATED.
   * Stale results (newer selection, cleared route, hidden mock) never
   * overwrite current state: the upgrade applies only to the zone + origin
   * this call was made for.
   */
  fun calculateRouteToSelectedZone() {
    val zone = _uiState.value.selectedSafeZone ?: return
    routingJob?.cancel()
    val origin = _uiState.value.userLocation
    val mode = _uiState.value.travelMode
    val hazards = _uiState.value.hazardZones
    lastRouteOrigin = origin
    val cacheKey = LiveRouteCache.key(zone.id, mode, origin, hazards)

    // A cached LIVE road route for the exact destination/mode/area/hazard set is
    // real road geometry and can be shown immediately.
    val cached = liveRouteCache.get(cacheKey)
    if (cached != null) {
      _uiState.update {
        it.copy(
          activeRoute = cached,
          alternativeRoutes = emptyList(),
          isCalculatingRoute = false,
          routeStatus = RouteStatus.READY,
          routeStatusMessage = "Cached OSRM road route to ${zone.name} — hazard-checked.",
          currentNavigationStepIndex = 0
        )
      }
      return
    }

    // NO straight line is drawn while we wait. The map stays clean until a real
    // road response arrives; the previous behaviour painted a 3-point
    // origin→midpoint→destination corridor, which reads as a real safe route.
    _uiState.update {
      it.copy(
        activeRoute = null,
        alternativeRoutes = emptyList(),
        isCalculatingRoute = true,
        routeStatus = RouteStatus.REQUESTING,
        routeStatusMessage = "Requesting a road route to ${zone.name}…",
        currentNavigationStepIndex = 0
      )
    }

    routingJob = viewModelScope.launch {
      val live = OsrmRoutingService.fetchLiveRoutesAsync(
        origin = origin,
        destination = zone.point,
        mode = mode,
        hazards = hazards,
        destinationName = zone.name,
        wantAlternatives = 1
      ).firstOrNull()

      // Staleness guard: a newer selection/origin must never be overwritten.
      if (lastRouteOrigin != origin || _uiState.value.selectedSafeZone?.id != zone.id) return@launch

      if (live == null) {
        _uiState.update {
          it.copy(
            activeRoute = null,
            isCalculatingRoute = false,
            routeStatus = RouteStatus.NETWORK_ERROR,
            routeStatusMessage = "No road route received (offline or router unavailable). " +
              "Nothing is drawn — an offline estimate is only offered if you ask for it."
          )
        }
        return@launch
      }
      if (live.pathPoints.isEmpty()) {
        _uiState.update {
          it.copy(
            activeRoute = null,
            isCalculatingRoute = false,
            routeStatus = RouteStatus.NO_ROUTE,
            routeStatusMessage = "The router returned no usable corridor to ${zone.name}."
          )
        }
        return@launch
      }

      // Road geometry received -> validate it against the live hazard picture
      // (hazardWarnings / routeSafetyStatus are produced by that check), then
      // publish. Only a READY route is ever drawn by the map.
      _uiState.update { it.copy(routeStatus = RouteStatus.VALIDATING_HAZARDS) }
      liveRouteCache.put(cacheKey, live)
      _uiState.update {
        it.copy(
          activeRoute = live,
          isCalculatingRoute = false,
          routeStatus = RouteStatus.READY,
          routeStatusMessage = "Live OSRM road route to ${zone.name} — " +
            "hazard-checked: ${live.routeSafetyStatus.label}",
          currentNavigationStepIndex = 0
        )
      }
    }
  }

  /**
   * Explicit opt-in for the OFFLINE straight-line estimate. This geometry does
   * not follow roads, so it is published only on a direct user request and is
   * labelled [RouteStatus.FALLBACK_UNVERIFIED] — never as a safe route.
   */
  fun requestOfflineFallbackRoute() {
    val zone = _uiState.value.selectedSafeZone ?: return
    val origin = _uiState.value.userLocation
    val mode = _uiState.value.travelMode
    val hazards = _uiState.value.hazardZones
    lastRouteOrigin = origin
    val fallback = OsrmRoutingService.calculateOfflineTacticalRoute(
      origin = origin,
      destination = zone.point,
      mode = mode,
      hazards = hazards,
      destinationName = zone.name
    )
    _uiState.update {
      it.copy(
        activeRoute = fallback,
        alternativeRoutes = emptyList(),
        isCalculatingRoute = false,
        routeStatus = RouteStatus.FALLBACK_UNVERIFIED,
        routeStatusMessage = "UNVERIFIED ESTIMATE — this is a direct hazard-skirting " +
          "line, NOT a road route and NOT validated against road closures. Use with caution.",
        currentNavigationStepIndex = 0
      )
    }
  }

  /**
   * Computes alternative corridors to the selected shelter so the user can
   * compare safety vs distance. Never silently dead: with no destination yet
   * it auto-selects the best-ranked shelter first, and with no feasible
   * shelter at all it says so instead of doing nothing.
   */
  fun loadAlternativeRoutes() {
    var zone = _uiState.value.selectedSafeZone
    if (zone == null) {
      val best = _uiState.value.rankedShelters.firstOrNull()
      if (best == null) {
        _uiState.update {
          it.copy(snackbarMessage = "No safe zone to route to — switch the simulated demo data on to see the India shelter network, then pick a green shelter")
        }
        return
      }
      selectSafeZone(best.zone, autoRoute = false)
      zone = best.zone
    }
    val target = zone
    routingJob?.cancel()
    val origin = _uiState.value.userLocation
    val mode = _uiState.value.travelMode
    val hazards = _uiState.value.hazardZones
    lastRouteOrigin = origin
    // Nothing is drawn until verified road corridors arrive (no straight-line
    // placeholder). Synthetic offline detours are filtered out below.
    _uiState.update {
      it.copy(
        activeRoute = null,
        alternativeRoutes = emptyList(),
        isCalculatingRoute = true,
        routeStatus = RouteStatus.REQUESTING,
        routeStatusMessage = "Requesting alternative road corridors to ${target.name}…",
        currentNavigationStepIndex = 0
      )
    }
    routingJob = viewModelScope.launch {
      val alternatives = OsrmRoutingService.calculateAlternativeRoutes(
        origin = origin,
        destination = target.point,
        mode = mode,
        hazards = hazards,
        destinationName = target.name,
        maxAlternatives = 2
      )
      if (lastRouteOrigin != origin ||
        _uiState.value.selectedSafeZone?.id != target.id
      ) return@launch

      // Only real road geometry is offered as an alternative. The offline
      // detour variants are not roads, so they are never presented as options.
      val roadAlternatives = alternatives.filter { it.isLiveOsrm }
      if (roadAlternatives.isEmpty()) {
        _uiState.update {
          it.copy(
            activeRoute = null,
            alternativeRoutes = emptyList(),
            isCalculatingRoute = false,
            routeStatus = RouteStatus.NETWORK_ERROR,
            routeStatusMessage = "No verified road alternatives available. Nothing is drawn."
          )
        }
        return@launch
      }
      _uiState.update {
        it.copy(
          activeRoute = roadAlternatives.first(),
          alternativeRoutes = roadAlternatives,
          isCalculatingRoute = false,
          routeStatus = RouteStatus.READY,
          routeStatusMessage = if (roadAlternatives.size > 1) {
            "${roadAlternatives.size} verified road corridors to ${target.name} — safest first"
          } else {
            "One verified road corridor found to ${target.name}"
          },
          currentNavigationStepIndex = 0
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
        routeStatus = RouteStatus.IDLE,
        routeStatusMessage = null,
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
    val best = _uiState.value.rankedShelters.firstOrNull()
    if (best == null) {
      _uiState.update {
        it.copy(snackbarMessage = "No feasible shelter right now — switch the simulated demo data on to see the India demo network")
      }
      return
    }
    selectSafeZone(best.zone, autoRoute = true)
    _uiState.update {
      it.copy(snackbarMessage = "Best safe zone selected: ${best.zone.name} — ${best.rankExplanation}")
    }
  }

  // =========================================================== NAVIGATION

  fun startEvacuationRoute() {
    var zone = _uiState.value.selectedSafeZone
    if (zone == null) {
      val best = _uiState.value.rankedShelters.firstOrNull()
      if (best == null) {
        _uiState.update {
          it.copy(snackbarMessage = "No safe zone to route to — switch the simulated demo data on to see the India shelter network, then pick a green shelter")
        }
        return
      }
      selectSafeZone(best.zone, autoRoute = false)
      zone = best.zone
    }
    val target = zone

    // Guidance may only start on a route that actually exists. Starting it on an
    // empty corridor produced a HUD with no geometry behind it.
    if (_uiState.value.activeRoute == null) {
      _uiState.update {
        it.copy(
          currentTab = ScreenTab.RADAR_MAP,
          snackbarMessage = "Requesting the road route to ${target.name}… guidance starts when it arrives"
        )
      }
      calculateRouteToSelectedZone()
      return
    }

    _uiState.update {
      it.copy(
        isNavigatingLive = true,
        currentTab = ScreenTab.RADAR_MAP,
        snackbarMessage = "Guidance to ${target.name} started"
      )
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
        isOfflineFirstMode = active,
        snackbarMessage = if (active) {
          "Offline-first mode ON — keep browsing to keep tiles; there is no bulk offline download in this build"
        } else {
          "Offline-first mode OFF"
        }
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
    refreshWeather(force = true)
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
            else -> "No GNews articles matched the search queries — try again later"
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
            "CACHED • ${feed.articles.size} articles • fetched $fetchedLabel"
          feed.articles.isNotEmpty() ->
            "ONLINE • ${feed.articles.size} articles • fetched $fetchedLabel"
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
        snackbarMessage = "Status updated on this device: marked SAFE"
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
    // BUG-1 FIX — the Local SOS flow now has a real COMPLETION state.
    //
    // Previously "Keep The Local SOS Active" (and back / outside-tap dismissal)
    // only closed the dialog and left `isSosActive = true` forever, so the global
    // ActiveToolsBar stayed injected above the screen content on every tab — the
    // reported "Home screen becomes broken after completing the SOS flow".
    // Dismissing the record dialog is now an explicit completion: the dialog
    // closes, the SOS flag and the safety switch reset, and the saved LOCAL
    // record is summarised exactly once.
    _uiState.update {
      it.copy(
        showSosBroadcastDialog = false,
        isSosActive = false,
        userIsSafe = true,
        snackbarMessage = it.lastReportReceipt?.note
          ?: "Local SOS record closed. Nothing was transmitted."
      )
    }
  }

  fun cancelSosBroadcast() {
    _uiState.update {
      it.copy(
        showSosBroadcastDialog = false,
        isSosActive = false,
        userIsSafe = true,
        snackbarMessage = "Local SOS canceled — nothing was transmitted or recorded as active"
      )
    }
  }

  fun dismissSosConfirmDialog() {
    _uiState.update { it.copy(showSosConfirmDialog = false) }
  }

  /**
   * Confirms the "Are you sure?" gate: saves the LOCAL SOS record (this build has
   * no relief-network backend, so nothing is transmitted to NDRF or any other
   * authority) and shows the record dialog.
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

  /**
   * Torch toggle. The state is EXPLICIT ([TorchState]) and the platform outcome
   * is reported back by MainActivity through [onTorchResult], so the UI can never
   * claim "ON" when the camera service refused. Deliberately NO snackbar: the
   * button's own ON/OFF state plus the global active-tools bar carry the state,
   * which is what made the old feedback look like an undismissable popup.
   */
  fun toggleFlashlight() {
    _uiState.update {
      if (it.torchState == TorchState.ON) {
        it.copy(torchState = TorchState.OFF, torchMessage = null)
      } else {
        it.copy(torchState = TorchState.ON, torchMessage = null)
      }
    }
  }

  /**
   * Real platform result for the torch request made by MainActivity.
   * [permissionDenied] distinguishes "ask again after granting" from
   * "this device cannot do it", both shown in place under the Light control.
   */
  fun onTorchResult(success: Boolean, reason: String? = null, permissionDenied: Boolean = false) {
    _uiState.update { state ->
      if (success && state.torchState == TorchState.ON) {
        state.copy(torchState = TorchState.ON, torchMessage = null)
      } else if (success) {
        // The user switched it off while the request was in flight.
        state.copy(torchState = TorchState.OFF, torchMessage = null)
      } else {
        state.copy(
          torchState = if (permissionDenied) TorchState.PERMISSION_DENIED else TorchState.UNAVAILABLE,
          torchMessage = reason ?: "This device did not allow the torch to turn on."
        )
      }
    }
  }

  /** Explicit start/stop for the siren; a second tap always stops it. */
  fun toggleSiren() {
    if (_uiState.value.sirenState == SirenState.PLAYING) stopSiren() else startSiren()
  }

  /**
   * Starts the siren with a live countdown and a hard auto-stop, so the tool can
   * never keep running unseen after the user leaves the Instructions screen.
   */
  fun startSiren() {
    sirenJob?.cancel()
    _uiState.update {
      it.copy(sirenState = SirenState.PLAYING, sirenSecondsLeft = SIREN_MAX_SECONDS)
    }
    sirenJob = viewModelScope.launch {
      var left = SIREN_MAX_SECONDS
      while (left > 0) {
        delay(1000)
        left--
        _uiState.update { it.copy(sirenSecondsLeft = left) }
      }
      // Auto-stop: state and hardware both return to IDLE together.
      _uiState.update { it.copy(sirenState = SirenState.IDLE, sirenSecondsLeft = 0) }
    }
  }

  /** Stops the siren immediately (used by the button and the global stop). */
  fun stopSiren() {
    sirenJob?.cancel()
    sirenJob = null
    _uiState.update { it.copy(sirenState = SirenState.IDLE, sirenSecondsLeft = 0) }
  }

  /** Global "stop everything" used by the always-visible active-tools bar. */
  fun stopAllDeviceTools() {
    stopSiren()
    _uiState.update { it.copy(torchState = TorchState.OFF, torchMessage = null) }
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
          reportId = "LOCAL-ERR",
          accepted = false,
          relayChannel = LocalEmergencyReportService.RELAY_CHANNEL,
          etaMinutes = null,
          note = "Could not save the local report: ${e.message ?: "unexpected error"}. Nothing was transmitted or recorded."
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

  /** Measures the REAL osmdroid tile-cache size from disk (Profile honesty). */
  fun updateTileCacheBytes() {
    val dir = tileCacheDirProvider() ?: return
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      val bytes = runCatching {
        dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
      }.getOrDefault(0L)
      kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        _uiState.update { it.copy(tileCacheBytes = bytes) }
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
