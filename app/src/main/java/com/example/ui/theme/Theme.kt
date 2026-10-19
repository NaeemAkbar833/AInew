package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
  primary = PrimaryBlue,
  onPrimary = Color.White,
  primaryContainer = PrimaryBlueContainer,
  onPrimaryContainer = OnPrimaryBlueContainer,
  secondary = SecondaryTeal,
  onSecondary = Color.White,
  secondaryContainer = SecondaryTealContainer,
  onSecondaryContainer = OnSecondaryTealContainer,
  tertiary = TertiaryAmber,
  onTertiary = Color.White,
  tertiaryContainer = TertiaryAmberContainer,
  background = BackgroundLight,
  onBackground = TextPrimaryLight,
  surface = SurfaceLight,
  onSurface = TextPrimaryLight,
  surfaceVariant = SurfaceVariantLight,
  onSurfaceVariant = TextSecondaryLight,
  outline = OutlineLight,
  outlineVariant = OutlineVariantLight,
)

private val DarkColorScheme = darkColorScheme(
  primary = PrimaryBlueDark,
  onPrimary = Color(0xFF0F172A),
  primaryContainer = PrimaryBlueContainerDark,
  onPrimaryContainer = Color.White,
  secondary = SecondaryTeal,
  onSecondary = Color.White,
  background = BackgroundDark,
  onBackground = TextPrimaryDark,
  surface = SurfaceDark,
  onSurface = TextPrimaryDark,
  surfaceVariant = SurfaceVariantDark,
  onSurfaceVariant = TextSecondaryDark,
  outline = OutlineDark,
)

@Composable
fun GradeScanTheme(
  darkTheme: Boolean = false, // Force consistent light theme matching reference screenshot
  dynamicColor: Boolean = false, // Keep consistent branding across devices
  content: @Composable () -> Unit,
) {
  // Always use LightColorScheme to guarantee consistent light theme across all devices and system settings
  val colorScheme = LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}
