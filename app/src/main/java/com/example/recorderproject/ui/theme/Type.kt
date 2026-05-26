package com.example.recorderproject.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.recorderproject.R

/**
 * Typography tokens for MEAT REC.
 *
 * Two font families:
 *   - Plus Jakarta Sans (display / body) — geometric sans with Thai support
 *   - JetBrains Mono (numerics) — tabular figures for level/timecode readouts
 *
 * Use Material3 styles via `MaterialTheme.typography.*` and custom styles
 * via `LocalAppTypography.current.*`.
 */

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular,  FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold,     FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium,  FontWeight.Medium),
)

private fun jakarta(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

private fun mono(
    weight: FontWeight,
    size: Int,
    letterSpacing: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = letterSpacing.sp,
)

val RecorderTypography = Typography(
    displayLarge   = jakarta(FontWeight.Bold,     57, 64, -0.5f),
    displayMedium  = jakarta(FontWeight.Bold,     45, 52,  0f),
    displaySmall   = jakarta(FontWeight.SemiBold, 36, 44,  0f),
    headlineLarge  = jakarta(FontWeight.SemiBold, 32, 40,  0f),
    headlineMedium = jakarta(FontWeight.SemiBold, 28, 36,  0f),
    headlineSmall  = jakarta(FontWeight.SemiBold, 24, 32,  0f),
    titleLarge     = jakarta(FontWeight.SemiBold, 22, 28,  0f),
    titleMedium    = jakarta(FontWeight.SemiBold, 16, 24,  0.15f),
    titleSmall     = jakarta(FontWeight.SemiBold, 14, 20,  0.1f),
    bodyLarge      = jakarta(FontWeight.Normal,   16, 24,  0.5f),
    bodyMedium     = jakarta(FontWeight.Normal,   14, 20,  0.25f),
    bodySmall      = jakarta(FontWeight.Normal,   12, 16,  0.4f),
    labelLarge     = jakarta(FontWeight.SemiBold, 14, 20,  0.1f),
    labelMedium    = jakarta(FontWeight.SemiBold, 12, 16,  0.5f),
    labelSmall     = jakarta(FontWeight.SemiBold, 11, 16,  0.5f),
)

/**
 * Custom typography styles outside the Material3 scale.
 * Access via `LocalAppTypography.current.*`.
 */
@Immutable
data class AppTypography(
    /** Caps section labels — "FILE", "RECORDINGS", "BIT DEPTH" */
    val labelTiny: TextStyle = jakarta(FontWeight.SemiBold, 10, 14, 1.5f),
    /** Hero numeric — timer, large take counter */
    val numericLarge: TextStyle = mono(FontWeight.Medium, 48, 0f),
    /** Level readout — dB, gain */
    val numericMedium: TextStyle = mono(FontWeight.Medium, 24, 0f),
    /** Inline numbers — Hz, ms, sample rate */
    val numericSmall: TextStyle = mono(FontWeight.Normal, 14, 0.5f),
)

val RecorderAppTypography = AppTypography()

val LocalAppTypography = staticCompositionLocalOf { AppTypography() }
