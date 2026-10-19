package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// =============================================================================
// GradeScan Stitch Typography System
// Bricolage Grotesque for display/headlines/titles + Figtree for body/labels
// =============================================================================

val BricolageGrotesqueFontFamily = FontFamily(
  Font(R.font.bricolage_grotesque, FontWeight.Normal),
  Font(R.font.bricolage_grotesque, FontWeight.Medium),
  Font(R.font.bricolage_grotesque, FontWeight.SemiBold),
  Font(R.font.bricolage_grotesque, FontWeight.Bold),
)

val FigtreeFontFamily = FontFamily(
  Font(R.font.figtree, FontWeight.Normal),
  Font(R.font.figtree, FontWeight.Medium),
  Font(R.font.figtree, FontWeight.SemiBold),
  Font(R.font.figtree, FontWeight.Bold),
)

val Typography = Typography(
  displayLarge = TextStyle(
    fontFamily = BricolageGrotesqueFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.5).sp,
  ),
  displayMedium = TextStyle(
    fontFamily = BricolageGrotesqueFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.3).sp,
  ),
  displaySmall = TextStyle(
    fontFamily = BricolageGrotesqueFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.25).sp,
  ),
  headlineLarge = TextStyle(
    fontFamily = BricolageGrotesqueFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.3).sp,
  ),
  headlineMedium = TextStyle(
    fontFamily = BricolageGrotesqueFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.2).sp,
  ),
  headlineSmall = TextStyle(
    fontFamily = BricolageGrotesqueFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.15).sp,
  ),
  titleLarge = TextStyle(
    fontFamily = BricolageGrotesqueFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.1).sp,
  ),
  titleMedium = TextStyle(
    fontFamily = BricolageGrotesqueFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp,
  ),
  titleSmall = TextStyle(
    fontFamily = FigtreeFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
  ),
  bodyLarge = TextStyle(
    fontFamily = FigtreeFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = FigtreeFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
  ),
  bodySmall = TextStyle(
    fontFamily = FigtreeFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 17.sp,
    letterSpacing = 0.1.sp,
  ),
  labelLarge = TextStyle(
    fontFamily = FigtreeFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp,
  ),
  labelMedium = TextStyle(
    fontFamily = FigtreeFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.2.sp,
  ),
  labelSmall = TextStyle(
    fontFamily = FigtreeFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.3.sp,
  ),
)
