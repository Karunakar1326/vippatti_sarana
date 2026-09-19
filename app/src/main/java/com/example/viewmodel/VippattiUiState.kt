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

enum class ScreenTab {
  RADAR_MAP,
  NEWS_DISPATCHES,
  INSTRUCTIONS,
  PROFILE
}

/**
 * Explicit device-tool states. The old `Boolean` pair could not express "the
 * platform refused the torch", which is why the UI claimed the light was ON
 * while nothing happened.
 */
enum class TorchState { OFF, ON, UNAVAILABLE, PERMISSION_DENIED }

enum class SirenState { IDLE, PLAYING }

/**
 * Explicit route lifecycle. A route is only ever DRAWN in [READY]; a straight
 * hazard-skirting corridor is never presented as a computed safe route.
 */
enum class RouteStatus {
  /** No destination selected yet. */
  IDLE,
  /** Request in flight — nothing is drawn. */
  REQUESTING,
  /** A routing response was received. */
  RECEIVED,
  /** Received geometry is being checked against the live hazard picture. */
  VALIDATING_HAZARDS,
  /** Validated road route ready to draw. */
  READY,
  /** The router answered but found no usable corridor. */
  NO_ROUTE,
  /** The router could not be reached (offline / server error). */
  NETWORK_ERROR,
  /** User explicitly asked for the offline straight-line estimate. */
  FALLBACK_UNVERIFIED
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
  /**
   * Offline-first display preference. There is NO bulk offline download in this
   * build, so this flag only says "I intend to work offline"; the Profile pack
   * reports the REAL measured tile-cache size and says "Not measured yet"
   * until it exists. Defaults to false (no "Ready" on a fresh install).
   */
  val isOfflineFirstMode: Boolean = false,
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
  /**
   * Device-tool state (torch/siren). Replaces the old boolean pair so the UI can
   * show an EXPLICIT, honest ON/OFF state — and the reason it failed — instead
   * of feedback that claims the torch is on when the platform refused it.
   */
  val torchState: TorchState = TorchState.OFF,
  /** Human-readable reason shown under the Light control when it cannot turn on. */
  val torchMessage: String? = null,
  val sirenState: SirenState = SirenState.IDLE,
  /** Seconds left before the siren auto-stops (drives the visible countdown). */
  val sirenSecondsLeft: Int = 0,
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
  /** True once a live Open-Meteo reading lands; false while weather is empty. */
  val isWeatherLive: Boolean = false,
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
  /** Mock-data compat flag — mirrors the radar "SIMULATED DEMO" toggle. */
  val isMockMode: Boolean = true,
  /**
   * SIMULATED demo-data visibility — the radar "SIMULATED DEMO" toggle.
   * ON (default) shows the labelled India multi-state network; OFF removes
   * ONLY the simulated zones (live provider events and citizen reports keep
   * flowing to the map and every engine).
   */
  val isMockDataVisible: Boolean = true,
  /** Citizen-submitted incident reports (unverified, TTL'd). */
  val userIncidentReports: List<IncidentReport> = emptyList(),
  /** Selected disaster event for the tap detail panel. */
  val disasterEventDetail: DisasterEvent? = null,
  /** Dialog for the "Add a Report" incident flow. */
  val showIncidentReportDialog: Boolean = false,

  // --- Intelligence results (recomputed on every location change) ---
  val hazardZones: List<HazardZone> = emptyList(),
  val safeZones: List<SafeZone> = PilotRegionData.safeZones,
  /** EVERY shelter evaluated (feasible AND rejected) — UI must show rejection reasons. */
  val evaluatedShelters: List<SafeZoneEvaluation> = emptyList(),
  /** Feasible-only, best-first — feeds routing/assignment (unchanged contract). */
  val rankedShelters: List<SafeZoneEvaluation> = emptyList(),
  val personalRisk: PersonalRiskAssessment? = null,
  val recommendedAction: RecommendedAction? = null,
  val relocationPlan: RelocationPlan? = null,

  // --- REAL tile-cache size (computed from disk, never fabricated) ---
  val tileCacheBytes: Long? = null,

  // --- Routing ---
  val selectedSafeZone: SafeZone? = null,
  val selectedEvaluation: SafeZoneEvaluation? = null,
  val activeRoute: RouteResult? = null,
  val alternativeRoutes: List<RouteResult> = emptyList(),
  val isCalculatingRoute: Boolean = false,
  /** Explicit routing lifecycle — drives the "no straight line" rule. */
  val routeStatus: RouteStatus = RouteStatus.IDLE,
  /** Honest, user-facing explanation of the current [routeStatus]. */
  val routeStatusMessage: String? = null,
  /** True when the drawn route is the explicitly-requested offline estimate. */
  val travelMode: String = "foot", // "foot" or "driving"
  val isNavigatingLive: Boolean = false,
  val currentNavigationStepIndex: Int = 0,

  // --- Detail sheets ---
  val hazardDetailZone: HazardZone? = null,
  val safeZoneDetail: SafeZone? = null
) {

  // --- Derived broadcast labels (REAL battery/GPS/relays - no hardcoded 84%) ---

  /** Explicit torch state exposed to the UI. Never a "maybe on" boolean. */
  val isFlashlightOn: Boolean get() = torchState == TorchState.ON

  /** Explicit siren state exposed to the UI. */
  val isSirenOn: Boolean get() = sirenState == SirenState.PLAYING

  /** True when a device tool is active (drives the global active-tools bar). */
  val hasActiveDeviceTool: Boolean get() = isFlashlightOn || isSirenOn

  /** True while a route request is in flight (spinner + "nothing drawn yet"). */
  val isRouteInFlight: Boolean
    get() = routeStatus == RouteStatus.REQUESTING ||
      routeStatus == RouteStatus.RECEIVED ||
      routeStatus == RouteStatus.VALIDATING_HAZARDS

  /**
   * True only when the drawn geometry is a validated, live road route. Any
   * offline estimate is NOT considered a safe route.
   */
  val hasValidatedRoute: Boolean
    get() = routeStatus == RouteStatus.READY && activeRoute?.isLiveOsrm == true


  /** Live device battery reading for SOS/report payloads. */
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

  /**
   * What an SOS actually reaches in THIS build. There is no relief-network
   * backend, so this must never claim an authority was notified.
   */
  val priorityRelaysLabel: String
    get() = "NOT TRANSMITTED — local record only • " + contactsList.size + " kin contacts saved"

  /**
   * Honest aggregate disaster-data status for the map UI. Derived ONLY from
   * real provider states — a cached source is never labeled LIVE.
   */
  val disasterDataStatusLabel: String
    get() {
      if (providerStates.isEmpty()) return "NOT SYNCED"
      val live = providerStates.count { it.isLive && it.eventCount >= 0 && it.statusMessage == null }
      val cached = providerStates.count { it.isFromCache }
      val degraded = providerStates.count { it.statusMessage != null }
      val last = disasterLastSyncMillis
      val stale = last != null && !com.example.data.disaster.DisasterCachePolicy.isRecent(last, System.currentTimeMillis())
      val parts = mutableListOf<String>()
      if (live > 0) parts += "LIVE"
      if (cached > 0) parts += "CACHED"
      if (degraded > 0) parts += "$degraded UNAVAILABLE"
      if (parts.isEmpty()) parts += if (stale) "STALE" else "NO DATA"
      return parts.joinToString(" • ")
    }

  /** Human-readable tile-cache size (e.g. "48.3 MB") or null until measured. */
  val tileCacheSizeLabel: String?
    get() = tileCacheBytes?.let { bytes ->
      when {
        bytes >= 1_000_000L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
      }
    }

  /**
   * Honest news-connection state for the Dispatches banner — derived from the
   * real sync label the news pipeline produced, never hardcoded "OFFLINE".
   */
  val newsConnectionStateLabel: String
    get() = when {
      isSyncing -> "SYNCING…"
      lastSyncTime.startsWith("ONLINE") -> "ONLINE • LIVE GNEWS FEED"
      lastSyncTime.startsWith("CACHED") -> "OFFLINE • CACHED FEED"
      else -> "NOT SYNCED"
    }
}
