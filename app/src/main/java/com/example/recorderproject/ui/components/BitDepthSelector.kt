package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderOrange

@Composable
fun BitDepthSelector(
    current: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val depths = listOf(16, 24, 32)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0C0C10))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (depth in depths) {
            val active = depth == current
            Text(
                text = "${depth}-bit",
                color = if (active) Color.White else RecorderBlueGrey,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) RecorderOrange else Color.Transparent)
                    .clickable { onChange(depth) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}
