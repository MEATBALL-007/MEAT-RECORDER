package com.example.recorderproject.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.ApplySaveMode
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange

@Composable
fun SaveModeSelector(
    current: ApplySaveMode,
    onChange: (ApplySaveMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0C0C10))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (mode in ApplySaveMode.values()) {
            val active = mode == current
            val scaleTarget = if (active) 1f else 0.96f
            val scale by animateFloatAsState(
                scaleTarget,
                spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                label = "saveModeScale${mode.name}",
            )
            val label = when (mode) {
                ApplySaveMode.BOTH -> "Keep both"
                ApplySaveMode.EQ_ONLY -> "Replace"
                ApplySaveMode.ORIGINAL_ONLY -> "Discard EQ"
            }
            Text(
                text = label,
                color = if (active) Color.White else RecorderBlueGrey,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .scale(scale)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (active) RecorderOrange else Color.Transparent)
                    .clickable { onChange(mode) }
                    .padding(vertical = 7.dp),
            )
        }
    }
}
