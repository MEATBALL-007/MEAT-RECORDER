package com.example.recorderproject.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * G26: Apple-style spring-scale wrapper for clickable surfaces.
 *
 * Usage:
 *   Modifier.interactiveScale().clickable { ... }
 *
 * Press → scales to 0.95 with spring; release → bounces back. Pairs with
 * subtle haptic feedback (light tap) for an iOS-feel.
 */
@Composable
fun Modifier.interactiveScale(
    pressedScale: Float = 0.95f,
    haptic: Boolean = true,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    pressed = true
                    if (haptic) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressed = false
                }
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "interactiveScale",
    )

    return this.scale(scale)
}
