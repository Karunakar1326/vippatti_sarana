package com.example.config

import android.util.Log
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppRemoteConfig(
    val homePadding: Int = 0,
    val primaryColorHex: String = "",
    val secondaryColorHex: String = "",
    val emergencyBannerText: String = "",
    val emergencyBannerEnabled: Boolean = false,
    val featureRadarEnabled: Boolean = true,
    val featureReportsEnabled: Boolean = true,
    val featureDispatchesEnabled: Boolean = true,
    val featureSafeZonesEnabled: Boolean = true,
    val appLogoUrl: String = "",
    val splashLogoUrl: String = ""
)

class RemoteConfigManager {
    private val remoteConfig = FirebaseRemoteConfig.getInstance()
    private val _configState = MutableStateFlow(AppRemoteConfig())
    val configState: StateFlow<AppRemoteConfig> = _configState.asStateFlow()

    init {
        // Use a safe production fetch interval (3600s = 1 hour)
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Local offline fallbacks
        val defaults = mapOf(
            "home_padding" to 0,
            "primary_color" to "",
            "secondary_color" to "",
            "emergency_banner_text" to "",
            "emergency_banner_enabled" to false,
            "feature_radar_enabled" to true,
            "feature_reports_enabled" to true,
            "feature_dispatches_enabled" to true,
            "feature_safe_zones_enabled" to true,
            "app_logo_url" to "",
            "splash_logo_url" to ""
        )
        remoteConfig.setDefaultsAsync(defaults)

        updateState()
        fetchAndActivate()
        setupRealtimeUpdates()
    }

    private fun fetchAndActivate() {
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                updateState()
            }
        }
    }

    private fun setupRealtimeUpdates() {
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                remoteConfig.activate().addOnCompleteListener {
                    updateState()
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                Log.w("RemoteConfigManager", "Real-time update error", error)
            }
        })
    }

    private fun updateState() {
        _configState.update {
            AppRemoteConfig(
                homePadding = remoteConfig.getLong("home_padding").toInt(),
                primaryColorHex = remoteConfig.getString("primary_color"),
                secondaryColorHex = remoteConfig.getString("secondary_color"),
                emergencyBannerText = remoteConfig.getString("emergency_banner_text"),
                emergencyBannerEnabled = remoteConfig.getBoolean("emergency_banner_enabled"),
                featureRadarEnabled = remoteConfig.getBoolean("feature_radar_enabled"),
                featureReportsEnabled = remoteConfig.getBoolean("feature_reports_enabled"),
                featureDispatchesEnabled = remoteConfig.getBoolean("feature_dispatches_enabled"),
                featureSafeZonesEnabled = remoteConfig.getBoolean("feature_safe_zones_enabled"),
                appLogoUrl = remoteConfig.getString("app_logo_url"),
                splashLogoUrl = remoteConfig.getString("splash_logo_url")
            )
        }
    }
}

object ConfigRegistry {
    val manager: RemoteConfigManager by lazy { RemoteConfigManager() }
}
