package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

data class CaptureMode(
    val name: String,
    val description: String,
    val sampleRate: Int,
    val bitDepth: Int,
    val noiseReduction: Boolean,
)

private val captureModes = listOf(
    CaptureMode("Field", "48 kHz / 24-bit · production sound, max headroom", 48_000, 24, false),
    CaptureMode("Podcast", "48 kHz / 16-bit · spoken word with auto-NR", 48_000, 16, true),
    CaptureMode("Music", "96 kHz / 24-bit · hi-res music capture", 96_000, 24, false),
    CaptureMode("Voice Memo", "44.1 kHz / 16-bit · quick + small files", 44_100, 16, true),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectorScreen(
    onPick: (CaptureMode) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture mode", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RecorderBlueGrey)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RecorderCharcoal),
            )
        },
        containerColor = RecorderCharcoal,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(captureModes) { m ->
                ModeCard(m, onTap = { onPick(m) })
            }
        }
    }
}

@Composable
private fun ModeCard(mode: CaptureMode, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RecorderCharcoalCard)
            .clickable(onClick = onTap)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(mode.name, color = RecorderYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(mode.description, color = RecorderBlueGrey, fontSize = 12.sp)
        }
        Text("→", color = RecorderOrange, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}
