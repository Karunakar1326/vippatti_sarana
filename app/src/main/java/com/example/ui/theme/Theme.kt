package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkTacticalColorScheme = darkColorScheme(
  primary = NeonEmerald,
  onPrimary = OnNeonEmerald,
  primaryContainer = NeonEmeraldContainer,
  onPrimaryContainer = OnNeonEmeraldContainer,
  secondary = TacticalCyan,
  onSecondary = OnTacticalCyan,
  secondaryContainer = TacticalCyanContainer,
  onSecondaryContainer = OnTacticalCyanContainer,
  background = ObsidianBg,
  onBackground = TacticalOnSurface,
  surface = ObsidianSurface,
  onSurface = TacticalOnSurface,
  surfaceVariant = ObsidianContainerHighest,
  onSurfaceVariant = TacticalOnSurfaceVariant,
  outline = TacticalOutline,
  outlineVariant = TacticalOutlineVariant,
  error = EmergencyRedBright,
  onError = OnEmergencyRed,
  errorContainer = EmergencyRedContainer,
  onErrorContainer = OnEmergencyRedContainer
)

private val LightTacticalColorScheme = lightColorScheme(
  primary = EmergencyRed,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFFEE2E2),
  onPrimaryContainer = Color(0xFF7F1D1D),
  secondary = Color(0xFF0284C7),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE0F2FE),
  onSecondaryContainer = Color(0xFF0369A1),
  background = Color(0xFFF8FAFC),
  onBackground = Color(0xFF0F172A),
  surface = Color.White,
  onSurface = Color(0xFF0F172A),
  surfaceVariant = Color(0xFFF1F5F9),
  onSurfaceVariant = Color(0xFF64748B),
  outline = Color(0xFFCBD5E1),
  outlineVariant = Color(0xFFE2E8F0),
  error = EmergencyRed,
  onError = Color.White,
  errorContainer = Color(0xFFFEE2E2),
  onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun VippattiTheme(
  darkTheme: Boolean = true, // Default to tactical night mode matching the screens
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkTacticalColorScheme else LightTacticalColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
