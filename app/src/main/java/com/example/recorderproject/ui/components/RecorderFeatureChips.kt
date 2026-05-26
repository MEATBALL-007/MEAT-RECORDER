package com.example.recorderproject.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

@Composable
fun RecorderFeatureChips(
    monitorOn: Boolean,
    liveEqOn: Boolean,
    liveNoiseGateOn: Boolean = false,
    micSourceLabel: String,
    onToggleMonitor: () -> Unit,
    onToggleLiveEq: () -> Unit,
    onToggleLiveNoiseGate: () -> Unit = {},
    onOpenSourcePicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier = Modifier) {}
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Chip(
                icon = "🎧",
                label = if (monitorOn) "Monitor ON" else "Monitor",
                active = monitorOn,
                onClick = onToggleMonitor,
                modifier = Modifier.weight(1f),
            )
            Chip(
                icon = "⚡",
                label = "Live EQ",
                active = liveEqOn,
                onClick = onToggleLiveEq,
                modifier = Modifier.weight(1f),
            )
            Chip(
                icon = "🎤",
                label = micSourceLabel.take(10),
                active = false,
                onClick = onOpenSourcePicker,
                modifier = Modifier.weight(1.1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Chip(
                icon = "🚫",
                label = if (liveNoiseGateOn) "NR Gate ON" else "NR Gate",
                active = liveNoiseGateOn,
                onClick = onToggleLiveNoiseGate,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Chip(
    icon: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        targetValue = if (active) RecorderOrange else Color(0xFF1F1F23),
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "chipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (active) Color.White else RecorderYellow,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "chipFg",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(icon, fontSize = 14.sp)
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
