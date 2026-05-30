package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.LoudnessTarget

private val Orange   = Color(0xFFFA4616)
private val BlueGrey = Color(0xFF7B8189)

@Composable
fun LoudnessTargetChip(
    current: LoudnessTarget,
    onSelectSession: (LoudnessTarget) -> Unit,
    onSaveAsDefault: (LoudnessTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    val bg = if (current is LoudnessTarget.Off) BlueGrey else Orange

    Row(
        modifier = modifier
            .background(bg.copy(alpha = 0.16f), shape = RoundedCornerShape(50))
            .clickable { showSheet = true }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(current.displayName.uppercase(), color = bg, fontSize = 12.sp)
    }

    if (showSheet) {
        LoudnessTargetSheet(
            current = current,
            onSelectSession = { sel -> onSelectSession(sel); showSheet = false },
            onSaveAsDefault = { sel -> onSaveAsDefault(sel); showSheet = false },
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoudnessTargetSheet(
    current: LoudnessTarget,
    onSelectSession: (LoudnessTarget) -> Unit,
    onSaveAsDefault: (LoudnessTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    var customLufs by remember { mutableStateOf((current as? LoudnessTarget.Custom)?.lufs ?: -16f) }
    var customTp   by remember { mutableStateOf((current as? LoudnessTarget.Custom)?.tpDbtp ?: -1f) }
    var customExpanded by remember { mutableStateOf(current is LoudnessTarget.Custom) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Loudness target", fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))

            TargetRow("Streaming –14", current == LoudnessTarget.Streaming) {
                onSelectSession(LoudnessTarget.Streaming)
            }
            TargetRow("Podcast –16", current == LoudnessTarget.Podcast) {
                onSelectSession(LoudnessTarget.Podcast)
            }
            TargetRow("Broadcast –23", current == LoudnessTarget.Broadcast) {
                onSelectSession(LoudnessTarget.Broadcast)
            }
            TargetRow("Custom", current is LoudnessTarget.Custom) {
                customExpanded = !customExpanded
            }
            if (customExpanded) {
                Text("Target LUFS: ${"%.1f".format(customLufs)}", fontSize = 12.sp)
                Slider(
                    value = customLufs,
                    onValueChange = { customLufs = (it * 2f).toInt() / 2f },
                    valueRange = -30f..-9f,
                )
                Text("TP ceiling: ${"%.1f".format(customTp)} dBTP", fontSize = 12.sp)
                Slider(
                    value = customTp,
                    onValueChange = { customTp = (it * 10f).toInt() / 10f },
                    valueRange = -3f..0f,
                )
                Button(
                    onClick = { onSelectSession(LoudnessTarget.Custom(customLufs, customTp)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Use these values")
                }
            }
            TargetRow("Off", current is LoudnessTarget.Off) {
                onSelectSession(LoudnessTarget.Off)
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onSaveAsDefault(current) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save current as default") }
        }
    }
}

@Composable
private fun TargetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
