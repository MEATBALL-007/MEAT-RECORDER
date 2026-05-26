package com.example.recorderproject.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape tokens — tighter than consumer Material default (16+ dp) to give
 * an instrument / pro-tool feel. Wired into MaterialTheme.shapes by Theme.kt.
 *
 * Existing screens that hardcode `RoundedCornerShape(12.dp)` keep working;
 * new and migrated screens read from `MaterialTheme.shapes.*`.
 */
val RecorderShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(6.dp),    // chips, small buttons
    medium     = RoundedCornerShape(8.dp),    // cards, panels
    large      = RoundedCornerShape(12.dp),   // dialogs, sheets
    extraLarge = RoundedCornerShape(20.dp),   // full-screen sheets, splash card
)
