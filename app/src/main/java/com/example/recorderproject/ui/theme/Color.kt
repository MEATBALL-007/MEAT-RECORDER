package com.example.recorderproject.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Legacy palette from the recovered Color.kt (mislabeled as Kmutt; project is MEATrec).
// Kept aliased so other recovered files still compile; slated for full removal in Phase 6.
val LegacyMaroon = Color(0xFFA31F34)
val LegacyGold = Color(0xFFD4AF37)

// Surfaces (existing — retained)
val SurfaceDark = Color(0xFF121212)
val SurfaceVariantDark = Color(0xFF1F1F1F)
val OnSurfaceDark = Color(0xFFE6E6E6)
val OnSurfaceVariantDark = Color(0xFFB3B3B3)

// MEATrec brand palette (Phase 1 spec 2026-05-25)
val RecorderOrange = Color(0xFFFA4616)   // primary accent — response curve, primary buttons
val RecorderYellow = Color(0xFFFFC72C)   // secondary accent — band handles, active state
val RecorderBlueGrey = Color(0xFF7B8189) // neutral — grid, dividers, spectrum hills, inactive UI
val RecorderCharcoal = Color(0xFF0C0C10) // canvas background
val RecorderCharcoalCard = Color(0xFF161618) // card / panel background

// Backwards-compat aliases (recovered files reference these names)
val KmuttMaroon = LegacyMaroon
val KmuttGold = LegacyGold
val MeatYellow = RecorderYellow
val MeatOrange = RecorderOrange
val MeatRed = Color(0xFFC0392B)
val TextPrimary = OnSurfaceDark
val TextSecondary = OnSurfaceVariantDark

@Immutable
data class AppColors(
    val primary: Color = RecorderOrange,
    val secondary: Color = RecorderYellow,
    val accentYellow: Color = RecorderYellow,
    val accentOrange: Color = RecorderOrange,
    val accentRed: Color = MeatRed,
    val surface: Color = RecorderCharcoal,
    val surfaceVariant: Color = RecorderCharcoalCard,
    val onSurface: Color = OnSurfaceDark,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary
)

val LocalAppColors = compositionLocalOf { AppColors() }
