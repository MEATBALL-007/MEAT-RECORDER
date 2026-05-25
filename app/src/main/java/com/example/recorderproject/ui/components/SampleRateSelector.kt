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
fun SampleRateSelector(
    current: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rates = listOf(44_100, 48_000, 96_000)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0C0C10))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (rate in rates) {
            val active = rate == current
            val label = when (rate) {
                44_100 -> "44.1"
                48_000 -> "48"
                96_000 -> "96"
                else -> "${rate / 1000}"
            }
            Text(
                text = "$label kHz",
                color = if (active) Color.White else RecorderBlueGrey,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) RecorderOrange else Color.Transparent)
                    .clickable { onChange(rate) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}
