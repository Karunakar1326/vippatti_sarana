package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.disaster.DisasterFileCache
import com.example.data.disaster.ZoneDetailMapper
import com.example.data.news.NewsFileCache
import com.example.ui.components.AddContactDialog
import com.example.ui.components.DisasterEventDetailDialog
import com.example.ui.components.IncidentReportDialog
import com.example.ui.components.EditProfileDialog
import com.example.ui.components.SituationReportDialog
import com.example.ui.components.InteractiveBagDialog
import com.example.ui.components.HazardZoneDetailDialog
import com.example.ui.components.SafeZoneDetailDialog
import com.example.ui.components.SosBroadcastDialog
import com.example.ui.components.SosConfirmDialog
import com.example.ui.components.VippattiBottomNavBar
import com.example.ui.screens.DispatchesScreen
import com.example.ui.screens.InstructionsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RadarMapScreen
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.VippattiTheme
import com.example.viewmodel.ScreenTab
import com.example.viewmodel.VippattiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      // Production ViewModel: file-backed GNews cache AND file-backed disaster
      // provider shards survive app restarts; the osmdroid tile-cache dir feeds
      // the REAL offline map-cache size shown on the Profile screen.
      val viewModel: VippattiViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VippattiViewModel(
              newsCache = NewsFileCache(File(cacheDir, "news_cache")),
              disasterCache = DisasterFileCache(File(cacheDir, "disaster_cache")),
              tileCacheDirProvider = { File(cacheDir, "osmdroid/tiles") }
            ) as T
        }
      )
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      VippattiTheme(darkTheme = uiState.isDarkTheme) {
        VippattiAppRoot(viewModel = viewModel)
      }
    }
  }
}

@Composable
fun VippattiAppRoot(
  viewModel: VippattiViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current

  // REAL device battery level.
  // Sticky ACTION_BATTERY_CHANGED broadcast gives an immediate reading.
  DisposableEffect(Unit) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        if (level >= 0 && scale > 0) {
          val percent = (level * 100 / scale).coerceIn(0, 100)
          val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
          viewModel.onBatteryChanged(percent, charging)
        }
      }
    }
    context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    onDispose { context.unregisterReceiver(receiver) }
  }

  // Real Hardware Flashlight Controller
  LaunchedEffect(uiState.isFlashlightOn) {
    try {
      val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
      val cameraId = cameraManager?.cameraIdList?.firstOrNull()
      if (cameraId != null) {
        cameraManager.setTorchMode(cameraId, uiState.isFlashlightOn)
      }
    } catch (e: Exception) {
      // Ignored if device lacks flash hardware or is camera-restricted
    }
  }

  // Real SOS Alarm Siren Audio Generator
  LaunchedEffect(uiState.isSirenOn) {
    if (uiState.isSirenOn) {
      withContext(Dispatchers.IO) {
        var toneGen: ToneGenerator? = null
        try {
          toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
          while (isActive && uiState.isSirenOn) {
            toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1200)
            delay(1400)
          }
        } catch (e: Exception) {
          // Graceful fallback if tone generator cannot acquire audio stream
        } finally {
          toneGen?.release()
        }
      }
    }
  }

  // Turn off flashlight on component disposal to avoid draining battery
  DisposableEffect(Unit) {
    onDispose {
      try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val cameraId = cameraManager?.cameraIdList?.firstOrNull()
        if (cameraId != null) {
          cameraManager.setTorchMode(cameraId, false)
        }
      } catch (_: Exception) {}
    }
  }

  // Measure the REAL osmdroid tile-cache size for the Profile honesty card.
  LaunchedEffect(Unit) { viewModel.updateTileCacheBytes() }

  LaunchedEffect(uiState.snackbarMessage) {
    uiState.snackbarMessage?.let { message ->
      snackbarHostState.showSnackbar(message)
      viewModel.clearSnackbar()
    }
  }

  // REAL TextToSpeech engine — speaks the bulletin the ViewModel composed from
  // real state (risk level, recommended action, live GNews headlines). The old
  // fake playback timer is gone: the engine itself reports completion.
  var ttsStatus by remember { mutableStateOf<Int?>(null) }
  val ttsEngine = remember { TextToSpeech(context) { status -> ttsStatus = status } }
  DisposableEffect(Unit) {
    onDispose {
      ttsEngine.stop()
      ttsEngine.shutdown()
    }
  }
  LaunchedEffect(uiState.isAudioPlaying, uiState.audioBulletinText, ttsStatus) {
    if (!uiState.isAudioPlaying) {
      ttsEngine.stop()
      return@LaunchedEffect
    }
    val bulletin = uiState.audioBulletinText
    if (bulletin.isBlank()) return@LaunchedEffect
    when (ttsStatus) {
      TextToSpeech.SUCCESS -> {
        ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String?) { }
          override fun onDone(utteranceId: String?) { viewModel.onTtsBulletinFinished() }
          override fun onError(utteranceId: String?) { viewModel.onTtsUnavailable() }
          override fun onError(utteranceId: String?, errorCode: Int) { viewModel.onTtsUnavailable() }
        })
        if (ttsEngine.speak(bulletin, TextToSpeech.QUEUE_FLUSH, null, "sarana_bulletin") == TextToSpeech.ERROR) {
          viewModel.onTtsUnavailable()
        }
      }
      null -> Unit // Engine still initializing — this effect re-runs when ttsStatus arrives.
      else -> viewModel.onTtsUnavailable()
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      VippattiBottomNavBar(
        currentTab = uiState.currentTab,
        onTabSelected = { viewModel.setTab(it) }
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(ObsidianSurface)
        .statusBarsPadding()
        .padding(bottom = innerPadding.calculateBottomPadding())
    ) {
      val remoteConfig by com.example.config.ConfigRegistry.manager.configState.collectAsStateWithLifecycle()

      Crossfade(
        targetState = uiState.currentTab,
        animationSpec = tween(durationMillis = 250),
        label = "tab_crossfade"
      ) { tab ->
        Column(modifier = Modifier.padding(remoteConfig.homePadding.dp)) {
            if (remoteConfig.emergencyBannerEnabled && remoteConfig.emergencyBannerText.isNotBlank()) {
                androidx.compose.material3.Text(
                    text = remoteConfig.emergencyBannerText,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.Red)
                        .padding(16.dp)
                )
            }
            if (remoteConfig.appLogoUrl.isNotBlank() && tab == ScreenTab.PROFILE) {
                // Example of placing remote logo on Profile tab (or it could be in a top bar)
                coil.compose.AsyncImage(
                    model = remoteConfig.appLogoUrl,
                    contentDescription = "App Logo",
                    modifier = Modifier.size(100.dp).align(androidx.compose.ui.Alignment.CenterHorizontally),
                    error = androidx.compose.ui.res.painterResource(id = android.R.drawable.sym_def_app_icon)
                )
            }

            // Screen Content
            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
          ScreenTab.NEWS_DISPATCHES -> DispatchesScreen(
            uiState = uiState,
            onSync = { viewModel.syncData() },
            onToggleAudio = { viewModel.toggleAudioBulletin() },
            onSelectCategory = { viewModel.setNewsCategory(it) },
            onNavigateToEvacRoute = { viewModel.startEvacuationRoute() },
            onNavigateTab = { viewModel.setTab(it) }
          )

          ScreenTab.          RADAR_MAP -> RadarMapScreen(
            uiState = uiState,
            onSelectBestSafeZone = { viewModel.selectBestSafeZone() },
            onSelectSafeZone = { viewModel.selectSafeZone(it) },
            onSetTravelMode = { viewModel.setTravelMode(it) },
            onStartEvacuation = { viewModel.startEvacuationRoute() },
            onStopEvacuation = { viewModel.stopLiveNavigation() },
            onNextNavigationStep = { viewModel.nextNavigationStep() },
            onLoadAlternativeRoutes = { viewModel.loadAlternativeRoutes() },
            onOpenSensorBroadcast = { viewModel.triggerSosBroadcast() },
            onClearRoute = { viewModel.clearActiveRoute() },
            // REAL hardware GPS fixes replace the India-centre fallback location.
            onRealGpsFix = { lat, lon -> viewModel.applyRealGpsFix(lat, lon) },
            onOpenHazardDetail = { viewModel.openHazardDetail(it) },
            onOpenSafeZoneDetail = { viewModel.openSafeZoneDetail(it) },
            // REAL disaster-data integration: layers, incident reports, event details.
            onToggleLayer = { viewModel.toggleLayer(it) },
            onOpenIncidentReport = { viewModel.openIncidentReportDialog() },
            onSubmitIncidentReport = { category, severity, description ->
              viewModel.submitIncidentReport(category, severity, description)
            },
            onOpenDisasterEventDetail = { viewModel.openDisasterEventDetail(it) },
            onDismissDisasterEventDetail = { viewModel.closeDisasterEventDetail() },
            onToggleMockData = { viewModel.toggleMockData() }
          )

          ScreenTab.INSTRUCTIONS -> InstructionsScreen(
            uiState = uiState,
            onToggleTheme = { viewModel.toggleTheme() },
            onToggleOfflineAccess = { viewModel.toggleOfflineCache(it) },
            
            onOpenInteractiveBag = { viewModel.openInteractiveBagDialog() },
            onToggleFlashlight = { viewModel.toggleFlashlight() },
            onToggleSiren = { viewModel.toggleSiren() }
          )

          ScreenTab.PROFILE -> ProfileScreen(
            uiState = uiState,
            onToggleTheme = { viewModel.toggleTheme() },
            onSetSafety = { viewModel.setUserSafety(it) },
            onBroadcastSos = { viewModel.triggerSosBroadcast() },
            onOpenAddContact = { viewModel.openAddContactDialog() },
            onOpenEditProfile = { viewModel.openEditProfileDialog() },
            onOpenSituationReport = { viewModel.openSituationReportDialog() }
          )
        }
       }
      }
      }

      // Modal Dialogs
      // Modal Dialogs
      // SOS confirmation gate — nothing is broadcast before an explicit YES.
      if (uiState.showSosConfirmDialog) {
        SosConfirmDialog(
          locationLabel = uiState.sosLocationLabel,
          batteryLabel = uiState.batteryLabel,
          onConfirm = { viewModel.confirmSosBroadcast() },
          onDismiss = { viewModel.dismissSosConfirmDialog() }
        )
      }

      if (uiState.showEditProfileDialog) {
        EditProfileDialog(
          profile = uiState.userProfile,
          onDismiss = { viewModel.closeEditProfileDialog() },
          onSave = { viewModel.updateUserProfile(it) }
        )
      }

      if (uiState.showSituationReportDialog) {
        SituationReportDialog(
          reporterName = uiState.userProfile.fullName,
          locationLabel = uiState.sosLocationLabel,
          batteryLabel = uiState.batteryLabel,
          isSubmitting = uiState.isSubmittingReport,
          onDismiss = { viewModel.closeSituationReportDialog() },
          onSubmit = { message, photoUri -> viewModel.submitSituationReport(message, photoUri) }
        )
      }

      if (uiState.showSosBroadcastDialog) {
        SosBroadcastDialog(
          locationLabel = uiState.sosLocationLabel,
          batteryLabel = uiState.batteryLabel,
          medicalTagLabel = uiState.userProfile.medicalTag,
          relaysLabel = uiState.priorityRelaysLabel,
          onDismiss = { viewModel.dismissSosDialog() },
          onCancelSos = { viewModel.cancelSosBroadcast() }
        )
      }

      if (uiState.showInteractiveBagDialog) {
        InteractiveBagDialog(
          items = uiState.goBagItems,
          onToggleItem = { viewModel.toggleGoBagItem(it) },
          onDismiss = { viewModel.closeInteractiveBagDialog() }
        )
      }

      // REAL disaster-event detail (tapped USGS/FIRMS/IMD/user marker).
      uiState.disasterEventDetail?.let { event ->
        DisasterEventDetailDialog(
          event = event,
          onDismiss = viewModel::closeDisasterEventDetail
        )
      }

      // Citizen incident reporting (USER_REPORT / REPORTED / unverified).
      if (uiState.showIncidentReportDialog) {
        IncidentReportDialog(
          locationLabel = uiState.sosLocationLabel,
          isGpsAvailable = !uiState.isUserLocationFallback,
          onDismiss = viewModel::closeIncidentReportDialog,
          onSubmit = { category, severityLabel, description ->
            viewModel.submitIncidentReport(category, severityLabel, description)
          }
        )
      }

      uiState.hazardDetailZone?.let { hazard ->
        // Disaster-aware detail flow: tapped zone -> linked backend event
        // (live provider / citizen report; null for mock-network zones) +
        // live viable shelters -> per-type mapped detail for the popup.
        val sourceEvent = ZoneDetailMapper.findSourceEvent(
          zone = hazard,
          providerEvents = uiState.disasterEvents,
          reportEvents = uiState.userIncidentReports.map { it.toDisasterEvent() }
        )
        HazardZoneDetailDialog(
          zone = hazard,
          detail = ZoneDetailMapper.map(
            zone = hazard,
            event = sourceEvent,
            feasibleSafeZones = uiState.rankedShelters.map { it.zone }
          ),
          onDismiss = { viewModel.closeHazardDetail() }
        )
      }

      uiState.safeZoneDetail?.let { shelter ->
        SafeZoneDetailDialog(
          zone = shelter,
          evaluation = uiState.selectedEvaluation?.takeIf { it.zone.id == shelter.id },
          onDismiss = { viewModel.closeSafeZoneDetail() },
          onSelectAndRoute = { viewModel.selectSafeZone(shelter) }
        )
      }
      if (uiState.showAddContactDialog) {
        AddContactDialog(
          onDismiss = { viewModel.closeAddContactDialog() },
          onAddContact = { name, rel, phone, loc ->
            viewModel.addContact(name, rel, phone, loc)
          }
        )
      }
    }
  }
}

