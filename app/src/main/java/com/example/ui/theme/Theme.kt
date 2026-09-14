package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * ONE source of truth for the application theme. VippattiTheme receives the
 * app-level isDarkTheme state (from VippattiViewModel.uiState) and provides
 * both the Material color scheme and the global [VippattiColors] palette, so
 * every token (NeonEmerald, ObsidianSurface, TacticalOnSurface, ...) resolves
 * theme-aware everywhere. Switching themes is instantaneous app-wide.
 */
val LocalVippattiColors = staticCompositionLocalOf { DarkVippattiColors }

private fun vippattiDarkScheme(p: VippattiColors) = darkColorScheme(
  primary = p.neonEmerald,
  onPrimary = p.onNeonEmerald,
  primaryContainer = p.neonEmeraldContainer,
  onPrimaryContainer = p.onNeonEmeraldContainer,
  secondary = p.tacticalCyan,
  onSecondary = p.onTacticalCyan,
  secondaryContainer = p.tacticalCyanContainer,
  onSecondaryContainer = p.onTacticalCyanContainer,
  background = p.obsidianBg,
  onBackground = p.tacticalOnSurface,
  surface = p.obsidianSurface,
  onSurface = p.tacticalOnSurface,
  surfaceVariant = p.obsidianContainerHighest,
  onSurfaceVariant = p.tacticalOnSurfaceVariant,
  outline = p.tacticalOutline,
  outlineVariant = p.tacticalOutlineVariant,
  error = p.emergencyRedBright,
  onError = p.onEmergencyRed,
  errorContainer = p.emergencyRedContainer,
  onErrorContainer = p.onEmergencyRedContainer
)

private fun vippattiLightScheme(p: VippattiColors) = lightColorScheme(
  primary = p.neonEmerald,
  onPrimary = p.onNeonEmerald,
  primaryContainer = p.neonEmeraldContainer,
  onPrimaryContainer = p.onNeonEmeraldContainer,
  secondary = p.tacticalCyan,
  onSecondary = p.onTacticalCyan,
  secondaryContainer = p.tacticalCyanContainer,
  onSecondaryContainer = p.onTacticalCyanContainer,
  background = p.obsidianBg,
  onBackground = p.tacticalOnSurface,
  surface = p.obsidianSurface,
  onSurface = p.tacticalOnSurface,
  surfaceVariant = p.obsidianContainerHighest,
  onSurfaceVariant = p.tacticalOnSurfaceVariant,
  outline = p.tacticalOutline,
  outlineVariant = p.tacticalOutlineVariant,
  error = p.emergencyRedBright,
  onError = p.onEmergencyRed,
  errorContainer = p.emergencyRedContainer,
  onErrorContainer = p.onEmergencyRedContainer
)

@Composable
fun VippattiTheme(
  darkTheme: Boolean = true, // Default to tactical night mode matching the screens
  content: @Composable () -> Unit,
) {
  val palette = if (darkTheme) DarkVippattiColors else LightVippattiColors
  val colorScheme = if (darkTheme) vippattiDarkScheme(palette) else vippattiLightScheme(palette)

  CompositionLocalProvider(LocalVippattiColors provides palette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}
