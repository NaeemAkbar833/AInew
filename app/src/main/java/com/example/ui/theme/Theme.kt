package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
  primary = PrimaryBlueLight,
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
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Keep consistent branding
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}

