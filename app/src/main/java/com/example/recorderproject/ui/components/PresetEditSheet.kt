package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SheetBg = Color(0xFF111111)
private val RowBg = Color(0xFF1A1A1A)
private val Orange = Color(0xFFE86A2B)
private val Yellow = Color(0xFFFFC72C)
private val Grey = Color(0xFF8892A4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditSheet(
    title: String = "Recording Preset",
    sampleRateInitial: Int = 48000,
    bitDepthInitial: Int = 24,
    channelCountInitial: Int = 1,
    noiseReductionInitial: Boolean = false,
    onApply: (sampleRate: Int, bitDepth: Int, channelCount: Int, noiseReduction: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var sampleRate by remember { mutableIntStateOf(sampleRateInitial) }
    var bitDepth by remember { mutableIntStateOf(bitDepthInitial) }
    var channelCount by remember { mutableIntStateOf(channelCountInitial) }
    var noiseReduction by remember { mutableStateOf(noiseReductionInitial) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            PresetLabel("Sample Rate")
            PresetPillRow(
                options = listOf(44100 to "44.1 kHz", 48000 to "48 kHz", 96000 to "96 kHz"),
                selected = sampleRate,
                onSelect = { sampleRate = it },
            )

            PresetLabel("Bit Depth")
            PresetPillRow(
                options = listOf(16 to "16-bit", 24 to "24-bit", 32 to "32-bit Float"),
                selected = bitDepth,
                onSelect = { bitDepth = it },
            )

            PresetLabel("Channels")
            PresetPillRow(
                options = listOf(1 to "Mono", 2 to "Stereo"),
                selected = channelCount,
                onSelect = { channelCount = it },
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RowBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Noise Reduction", color = Color.White, fontWeight = FontWeight.Medium)
                    Text("Remove background hiss", color = Grey, fontSize = 11.sp)
                }
                Switch(
                    checked = noiseReduction,
                    onCheckedChange = { noiseReduction = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Orange,
                        checkedTrackColor = Orange.copy(alpha = 0.4f),
                    ),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Grey),
                ) { Text("Cancel") }
                Button(
                    onClick = { onApply(sampleRate, bitDepth, channelCount, noiseReduction); onDismiss() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                ) { Text("Apply", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PresetLabel(text: String) {
    Text(text, color = Grey, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun <T> PresetPillRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) {
            val isSelected = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Orange else RowBg)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSelected) Color.White else Grey,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
