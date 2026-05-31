package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Orange   = Color(0xFFFA4616)
private val Yellow   = Color(0xFFFFC72C)
private val BlueGrey = Color(0xFF7B8189)

/**
 * Two side-by-side increment buttons.
 *  - "+1 TAKE"  — bumps the take number in the current file name (Scene_1_T01 → Scene_1_T02).
 *  - "+0.1 SCENE" — bumps the scene to next sub-scene (Scene 1 → Scene 1.1); take resets to 01.
 *
 * Use before tapping record when you want to manually skip a take number, or move
 * to a sub-scene without renaming by hand.
 */
@Composable
fun TakeSceneBumpRow(
    onBumpTake: () -> Unit,
    onBumpSubscene: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BumpButton(label = "+1 TAKE",   accent = Orange, onClick = onBumpTake, modifier = Modifier.weight(1f))
        BumpButton(label = "+0.1 SCENE", accent = Yellow, onClick = onBumpSubscene, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BumpButton(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(accent.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
    }
}
