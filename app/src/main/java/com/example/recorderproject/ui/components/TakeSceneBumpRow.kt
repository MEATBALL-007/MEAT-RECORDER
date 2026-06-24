package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
 * Manual take / scene increment + decrement buttons.
 *
 *  Row 1 (TAKE):  [-1]  [+1]
 *  Row 2 (SCENE): [-1]  [-0.1]  [+0.1]  [+1]
 *
 * All buttons mutate the current file-name + scene-name state via the VM.
 * Take is clamped to >= 1; scene whole number clamped to >= 1; scene fraction
 * clamped to >= 0 (going below collapses back to integer form).
 */
@Composable
fun TakeSceneBumpRow(
    onTakeMinus: () -> Unit,
    onTakePlus: () -> Unit,
    onSceneMinus: () -> Unit,
    onSceneMinusDot1: () -> Unit,
    onSceneDot1: () -> Unit,
    onScenePlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowLabel("TAKE")
            BumpButton(label = "−1", accent = Orange, onClick = onTakeMinus, modifier = Modifier.weight(1f))
            BumpButton(label = "+1", accent = Orange, onClick = onTakePlus,  modifier = Modifier.weight(1f))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowLabel("SCENE")
            BumpButton(label = "−1",   accent = Yellow, onClick = onSceneMinus,    modifier = Modifier.weight(1f))
            BumpButton(label = "−0.1", accent = Yellow, onClick = onSceneMinusDot1, modifier = Modifier.weight(1f))
            BumpButton(label = "+0.1", accent = Yellow, onClick = onSceneDot1,     modifier = Modifier.weight(1f))
            BumpButton(label = "+1",   accent = Yellow, onClick = onScenePlus,     modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowLabel(text: String) {
    Text(
        text = text,
        color = BlueGrey,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(end = 6.dp),
    )
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
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp,
        )
    }
}
