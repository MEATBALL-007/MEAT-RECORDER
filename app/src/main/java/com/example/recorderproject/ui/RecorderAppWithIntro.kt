package com.example.recorderproject.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.recorderproject.ui.theme.LocalReduceMotion

/**
 * Wrapper composable that plays a fade+scale intro (SplashScreen) before revealing [content].
 *
 * Port-back of the old `RecorderAppWithIntro` API. The old MEATrec wrapped the entire
 * recorder UI in this so the splash → app transition felt like one smooth motion rather
 * than two separate routes.
 *
 * @param introDurationMs Max time the splash is held before forcing transition (fallback
 *   in case SplashScreen's own animation timer never completes). Old app used ~3.2 s.
 *   When [LocalReduceMotion] is true this is collapsed to 0 (skip intro entirely).
 * @param content The actual app body to reveal once intro finishes.
 */
@Composable
fun RecorderAppWithIntro(
    introDurationMs: Long = 3_200L,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    var introDone by remember { mutableStateOf(reduceMotion) }

    // Belt-and-suspenders: even if SplashScreen never calls its own onDone for some reason,
    // force-advance after introDurationMs so the user is never trapped on splash.
    LaunchedEffect(Unit) {
        if (!introDone) {
            kotlinx.coroutines.delay(introDurationMs)
            introDone = true
        }
    }

    AnimatedContent(
        targetState = introDone,
        transitionSpec = {
            // Splash exits with quick fade + slight zoom-in (feels like camera pulling forward).
            // Content enters with fade + slight scale-up settle.
            (fadeIn(tween(420)) + scaleIn(initialScale = 0.96f, animationSpec = tween(420)))
                .togetherWith(
                    fadeOut(tween(280)) + scaleOut(targetScale = 1.04f, animationSpec = tween(280))
                )
        },
        label = "introToApp",
        modifier = Modifier.fillMaxSize(),
    ) { done ->
        if (!done) {
            SplashScreen(onDone = { introDone = true })
        } else {
            content()
        }
    }
}
