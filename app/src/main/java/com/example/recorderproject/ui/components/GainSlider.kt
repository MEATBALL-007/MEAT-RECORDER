package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

@Composable
fun GainSlider(
    valueDb: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("INPUT GAIN", color = RecorderBlueGrey, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (valueDb == 0f) "0.0 dB" else String.format("%+.1f dB", valueDb),
                color = if (valueDb == 0f) RecorderBlueGrey else RecorderYellow,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
        Slider(
            value = valueDb,
            onValueChange = onChange,
            valueRange = -12f..24f,
            colors = SliderDefaults.colors(
                thumbColor = RecorderYellow,
                activeTrackColor = RecorderOrange,
                inactiveTrackColor = RecorderBlueGrey.copy(alpha = 0.4f),
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("-12", color = RecorderBlueGrey, fontSize = 10.sp)
            Text("0", color = RecorderBlueGrey, fontSize = 10.sp)
            Text("+24", color = RecorderBlueGrey, fontSize = 10.sp)
        }
        // F10: quick gain presets — one-tap to common values
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-6f, 0f, 6f, 12f).forEach { preset ->
                val active = valueDb == preset
                Text(
                    text = if (preset == 0f) "0" else String.format("%+.0f", preset),
                    color = if (active) RecorderOrange else RecorderBlueGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) RecorderOrange.copy(alpha = 0.15f) else Color(0xFF0C0C10))
                        .clickable { onChange(preset) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}
