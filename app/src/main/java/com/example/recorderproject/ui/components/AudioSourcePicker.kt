package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.audio.UsbAudioDetector
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/** Phase 3 — Audio source picker. Lists built-ins + detected USB-C devices (VM40, Røde, etc.). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSourcePicker(
    currentSourceName: String,
    usbDevices: List<UsbAudioDetector.UsbDevice>,
    onPickBuiltin: (String) -> Unit,
    onPickUsb: (UsbAudioDetector.UsbDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = RecorderCharcoalCard,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("AUDIO SOURCE", color = RecorderYellow, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)

            Header("Built-in")
            for (src in listOf("Microphone", "Camcorder", "Voice Recognition", "Unprocessed")) {
                SourceRow(src, current = src == currentSourceName) { onPickBuiltin(src); onDismiss() }
            }

            if (usbDevices.isNotEmpty()) {
                Header("USB-C devices")
                for (dev in usbDevices) {
                    SourceRow(
                        title = dev.productName,
                        subtitle = "${dev.sampleRates.joinToString(",")} Hz · ${dev.channelCounts.joinToString(",")} ch",
                        current = false,
                    ) { onPickUsb(dev); onDismiss() }
                }
            } else {
                Header("USB-C devices")
                Text("Plug in VM40 or any UAC-compliant mic to see it here.",
                    color = RecorderBlueGrey, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, color = RecorderBlueGrey, fontSize = 11.sp,
        letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun SourceRow(title: String, subtitle: String? = null, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0C0C10))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(title, color = if (current) RecorderOrange else Color.White, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, color = RecorderBlueGrey, fontSize = 11.sp) }
        }
        if (current) Text("●", color = RecorderOrange, fontWeight = FontWeight.Bold)
    }
}
