package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlin.math.exp
import kotlin.math.ln

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EQBandSheet(
    band: EQBand,
    onChange: (EQBand) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RecorderCharcoalCard,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BAND ${band.id}", color = RecorderYellow, fontWeight = FontWeight.SemiBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
            }

            TypePicker(band.type, onPicked = { onChange(band.copy(type = it)) })

            ParamSlider(
                label = "Frequency",
                value = freqToSliderT(band.frequencyHz),
                onChange = { onChange(band.copy(frequencyHz = sliderTToFreq(it))) },
                readout = formatHz(band.frequencyHz),
            )

            ParamSlider(
                label = "Gain",
                value = (band.gainDb + 24f) / 48f,
                onChange = { onChange(band.copy(gainDb = it * 48f - 24f)) },
                readout = String.format("%+.1f dB", band.gainDb),
            )

            ParamSlider(
                label = "Q",
                value = (band.q - 0.1f) / 17.9f,
                onChange = { onChange(band.copy(q = 0.1f + it * 17.9f)) },
                readout = String.format("%.2f", band.q),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("Enabled", band.enabled) { onChange(band.copy(enabled = !band.enabled)) }
                ToggleChip("Mute", band.muted) { onChange(band.copy(muted = !band.muted)) }
                ToggleChip("Solo", band.soloed) { onChange(band.copy(soloed = !band.soloed)) }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close", color = RecorderOrange)
            }
        }
    }
}

@Composable
private fun TypePicker(current: EQBandType, onPicked: (EQBandType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Type", color = RecorderBlueGrey, modifier = Modifier.padding(end = 12.dp))
        TextButton(onClick = { expanded = true }) {
            Text(current.displayName, color = RecorderYellow)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (t in EQBandType.values()) {
                DropdownMenuItem(text = { Text(t.displayName) }, onClick = { onPicked(t); expanded = false })
            }
        }
    }
}

@Composable
private fun ParamSlider(label: String, value: Float, onChange: (Float) -> Unit, readout: String) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = RecorderBlueGrey)
            Text(readout, color = RecorderYellow, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = RecorderYellow,
                activeTrackColor = RecorderOrange,
                inactiveTrackColor = RecorderBlueGrey.copy(alpha = 0.4f),
            ),
        )
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onTap: () -> Unit) {
    Text(
        text = label,
        color = if (on) Color.White else RecorderBlueGrey,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) RecorderOrange else Color(0xFF0C0C10))
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
    )
}

private fun freqToSliderT(f: Float): Float {
    val t = (ln(f.toDouble()) - ln(20.0)) / (ln(20_000.0) - ln(20.0))
    return t.toFloat().coerceIn(0f, 1f)
}

private fun sliderTToFreq(t: Float): Float =
    exp(ln(20.0) + t.toDouble() * (ln(20_000.0) - ln(20.0))).toFloat()

private fun formatHz(f: Float): String = when {
    f >= 1000f -> String.format("%.1f kHz", f / 1000f)
    else -> String.format("%.0f Hz", f)
}
