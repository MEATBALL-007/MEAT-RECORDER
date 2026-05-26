package com.example.recorderproject.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion tokens for MEAT REC.
 *
 * Duration constants are in milliseconds — pass directly to `tween(Duration.medium)`,
 * `spring(...)`, `delay(Duration.fast.toLong())`, etc.
 *
 * Easing curves follow Material Motion guidance; emphasized curves are for
 * hero animations, standard curves for default UI motion.
 *
 * Reduce-motion: read `LocalReduceMotion.current` and substitute a snap or
 * shortened tween. The roadmap commits to honoring this on every animation
 * in a later motion-polish phase; this file just exposes the hook.
 */
object Duration {
    const val instant = 80     // press feedback, micro-touches
    const val fast    = 160    // toggles, small fades
    const val medium  = 280    // standard transitions
    const val slow    = 480    // hero animations, splash
    const val verySlow = 800   // ambient (breathing rings, pulse)
}

object Easing {
    val standard        = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val emphasized      = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val emphasizedAccel = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val emphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
}

val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun ReducedMotionProvider(reduce: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReduceMotion provides reduce, content = content)
}
