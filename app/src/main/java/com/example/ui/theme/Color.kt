package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ==========================================
// GradeScan Stitch Design System - Core Tokens
// ==========================================

// Primary Blue Palette
val PrimaryBlue = Color(0xFF2A4FD6)
val PrimaryBlueHover = Color(0xFF1F3FBF)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFDDE5FF)
val OnPrimaryContainer = Color(0xFF141A2B)

// Stitch Header Gradient (#1F3FBF -> #2A4FD6)
val HeaderGradientStart = Color(0xFF1F3FBF)
val HeaderGradientEnd = Color(0xFF2A4FD6)
val HeaderBlueGradient = Brush.verticalGradient(
  colors = listOf(HeaderGradientStart, HeaderGradientEnd)
)
val HeaderBlueGradientHorizontal = Brush.horizontalGradient(
  colors = listOf(HeaderGradientStart, HeaderGradientEnd)
)

// Stitch Primary Action Gradient (#1F3FBF -> #4262E6)
val BlueGradientStart = Color(0xFF1F3FBF)
val BlueGradientEnd = Color(0xFF4262E6)
val PrimaryBlueGradient = Brush.horizontalGradient(
  colors = listOf(BlueGradientStart, BlueGradientEnd)
)
val PrimaryBlueGradientVertical = Brush.verticalGradient(
  colors = listOf(HeaderGradientStart, HeaderGradientEnd)
)

// Stitch Light Theme Tokens
val BackgroundLight = Color(0xFFEEF2F9)
val SurfaceLight = Color(0xFFFFFFFF)
val CardLight = Color(0xFFFFFFFF)
val OutlineLight = Color(0xFFD1D9EC)
val TextPrimaryLight = Color(0xFF141A2B)
val TextSecondaryLight = Color(0xFF566077)
val SuccessLight = Color(0xFF16A34A)
val WarningLight = Color(0xFFDC2626)
val ErrorLight = Color(0xFFDC2626)

// Soft Tinted Surfaces & Accents
val SoftBlueTint = Color(0xFFF0F4FF)
val AccentViolet = Color(0xFF7C3AED)
val AccentVioletContainer = Color(0xFFF3E8FF)
val AccentTeal = Color(0xFF0D9488)
val AccentTealContainer = Color(0xFFCCFBF1)

// Dark Theme (fallback)
val BackgroundDark = Color(0xFF0B0F1E)
val SurfaceDark = Color(0xFF131830)
val CardDark = Color(0xFF1A2036)
val OutlineDark = Color(0xFF2A3250)
val TextPrimaryDark = Color(0xFFE3E7F4)
val TextSecondaryDark = Color(0xFFA2AAC2)
val PrimaryBlueDark = Color(0xFFAFC4FF)
val OnPrimaryDark = Color(0xFF0A1D66)
val PrimaryContainerDark = Color(0xFF1B2A63)
val OnPrimaryContainerDark = Color(0xFFE3E7F4)
val SuccessDark = Color(0xFF22B573)
val WarningDark = Color(0xFFE5484D)
val ErrorDark = Color(0xFFE5484D)

// Status & Semantic Colors
val StatusSuccess = Color(0xFF16A34A)
val StatusWarning = Color(0xFFDC2626)
val StatusAmber = Color(0xFFD97706)
val StatusInfo = Color(0xFF2A4FD6)

// Backward Compatibility Aliases & Semantic Tokens
val PrimaryBlueLight = PrimaryBlue
val PrimaryBlueContainer = PrimaryContainer
val OnPrimaryBlueContainer = OnPrimaryContainer
val PrimaryBlueContainerDark = PrimaryContainerDark

val SecondaryTeal = Color(0xFF0D9488)
val SecondaryTealContainer = Color(0xFFCCFBF1)
val OnSecondaryTealContainer = Color(0xFF115E59)

val TertiaryAmber = Color(0xFFD97706)
val TertiaryAmberContainer = Color(0xFFFEF3C7)
val OnTertiaryAmberContainer = Color(0xFFB45309)

val SurfaceVariantLight = Color(0xFFF5F7FC)
val SurfaceVariantDark = SurfaceDark
val OutlineVariantLight = OutlineLight

// Semantic Aliases used across screens
val RoyalBlue = PrimaryBlue
val DarkRoyalBlue = PrimaryBlueHover
val LightBlueHighlight = PrimaryContainer
val BorderGray = OutlineLight
val TextPrimary = TextPrimaryLight
val TextSecondary = TextSecondaryLight
val DisabledGray = TextSecondaryLight
val DisabledBackground = Color(0xFFEEF2F9)
val StatusSuccessContainer = Color(0xFFDCFCE7)
val StatusWarningContainer = Color(0xFFFEE2E2)
val StatusInfoContainer = PrimaryContainer
val DeepNavy = PrimaryBlue
val DeepNavyHover = PrimaryBlueHover
val DeepNavyLight = PrimaryBlueLight
val DeepNavyContainer = PrimaryContainer
val OnDeepNavyContainer = OnPrimaryContainer
val DeepNavyDark = PrimaryBlueDark
val OnDeepNavyDark = OnPrimaryDark
val DeepNavyContainerDark = PrimaryContainerDark
val OnDeepNavyContainerDark = OnPrimaryContainerDark
val RestrainedTeal = SecondaryTeal
val RestrainedTealHover = SecondaryTeal
val RestrainedTealContainer = SecondaryTealContainer
val OnRestrainedTealContainer = OnSecondaryTealContainer
val RestrainedTealDark = SecondaryTeal
val TextTertiaryLight = TextSecondaryLight
val TextTertiaryDark = TextSecondaryDark

val SplashGradient = Brush.linearGradient(colors = listOf(Color(0xFF1F3FBF), Color(0xFF2A4FD6)))
val SplashGradientBrush = SplashGradient

