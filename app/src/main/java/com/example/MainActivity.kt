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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AddContactDialog
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

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: VippattiViewModel = viewModel()
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

  // REAL device battery level — replaces the demo "84% (Low Drain Mode)".
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

  LaunchedEffect(uiState.snackbarMessage) {
    uiState.snackbarMessage?.let { message ->
      snackbarHostState.showSnackbar(message)
      viewModel.clearSnackbar()
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
      Crossfade(
        targetState = uiState.currentTab,
        animationSpec = tween(durationMillis = 250),
        label = "tab_crossfade"
      ) { tab ->
        when (tab) {
          ScreenTab.NEWS_DISPATCHES -> DispatchesScreen(
            uiState = uiState,
            onSync = { viewModel.syncData() },
            onToggleAudio = { viewModel.toggleAudioBulletin() },
            onSelectCategory = { viewModel.setNewsCategory(it) },
            onNavigateToEvacRoute = { viewModel.startEvacuationRoute() },
            onNavigateTab = { viewModel.setTab(it) }
          )

          ScreenTab.RADAR_MAP -> RadarMapScreen(
            uiState = uiState,
            onSelectBestSafeZone = { viewModel.selectBestSafeZone() },
            onSelectSafeZone = { viewModel.selectSafeZone(it) },
            onSetTravelMode = { viewModel.setTravelMode(it) },
            onStartEvacuation = { viewModel.startEvacuationRoute() },
            onStopEvacuation = { viewModel.stopLiveNavigation() },
            onNextNavigationStep = { viewModel.nextNavigationStep() },
            onLoadAlternativeRoutes = { viewModel.loadAlternativeRoutes() },
            onOpenSensorBroadcast = { viewModel.triggerSosBroadcast() },
            onOsmRouteUpdated = { dist, dur, summary, isLive ->
              viewModel.onOsmRouteUpdated(dist, dur, summary, isLive)
            },
            // REAL hardware GPS fixes replace the static pilot location (Painavu, Idukki, Kerala, India).
            onRealGpsFix = { lat, lon -> viewModel.applyRealGpsFix(lat, lon) },
            onOpenHazardDetail = { viewModel.openHazardDetail(it) },
            onOpenSafeZoneDetail = { viewModel.openSafeZoneDetail(it) }
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

      uiState.hazardDetailZone?.let { hazard ->
        HazardZoneDetailDialog(
          zone = hazard,
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

