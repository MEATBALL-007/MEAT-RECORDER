package com.example.recorderproject.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// KMUTT brand palette
val KmuttMaroon = Color(0xFFA31F34)
val KmuttGold = Color(0xFFD4AF37)

// Surfaces
val SurfaceDark = Color(0xFF121212)
val SurfaceVariantDark = Color(0xFF1F1F1F)
val OnSurfaceDark = Color(0xFFE6E6E6)
val OnSurfaceVariantDark = Color(0xFFB3B3B3)

// "Meat Recorder" accent palette — TODO: original values lost 2026-05-25,
// these are best-guess placeholders matching the warm meat/brand vibe.
val MeatYellow = Color(0xFFFFC857)
val MeatOrange = Color(0xFFE58A2E)
val MeatRed = Color(0xFFC0392B)
val TextPrimary = OnSurfaceDark
val TextSecondary = OnSurfaceVariantDark

@Immutable
data class AppColors(
    val primary: Color = KmuttMaroon,
    val secondary: Color = KmuttGold,
    val accentYellow: Color = MeatYellow,
    val accentOrange: Color = MeatOrange,
    val accentRed: Color = MeatRed,
    val surface: Color = SurfaceDark,
    val surfaceVariant: Color = SurfaceVariantDark,
    val onSurface: Color = OnSurfaceDark,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary
)

val LocalAppColors = compositionLocalOf { AppColors() }
