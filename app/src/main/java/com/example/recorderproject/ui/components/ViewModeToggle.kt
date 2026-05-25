package com.example.recorderproject.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.EQViewMode
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange

@Composable
fun ViewModeToggle(
    current: EQViewMode,
    onChange: (EQViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF0C0C10))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (mode in EQViewMode.values()) {
            val active = mode == current
            val scaleTarget = if (active) 1f else 0.96f
            val scale by animateFloatAsState(
                scaleTarget,
                spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                label = "viewModeScale${mode.name}",
            )
            Text(
                text = if (mode == EQViewMode.TWO_D) "2D" else "3D",
                modifier = Modifier
                    .scale(scale)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (active) RecorderOrange else Color.Transparent)
                    .clickable { onChange(mode) }
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                color = if (active) Color.White else RecorderBlueGrey,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
