package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.audio.RoomProfile
import com.example.recorderproject.audio.RoomProfiler
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room acoustic profiler screen.
 *
 * Captures 2 s of ambient audio from the mic and reports:
 *   - Noise floor in dB (lower = quieter room)
 *   - RT60 estimate in ms (rough reverb tail)
 *   - Dominant resonance frequency
 *   - Quality stars 1..5 (combined heuristic)
 *
 * Phase E port-back. Old MEATrec had this as an in-line card; we make it a
 * dedicated screen so the user can re-run + read results without UI clutter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomProfilerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val profiler = remember { RoomProfiler(context) }
    var profile by remember { mutableStateOf<RoomProfile?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var trigger by remember { mutableStateOf(0) }

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            analyzing = true
            profile = withContext(Dispatchers.IO) { profiler.analyze(durationMs = 2_000L) }
            analyzing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Room profiler", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
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
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Analyze 2s of ambient audio to estimate noise floor, reverb, and dominant resonance. Stand still and stay quiet during capture.",
                color = RecorderBlueGrey,
                fontSize = 11.sp,
            )

            Button(
                onClick = { trigger++ },
                enabled = !analyzing,
                colors = ButtonDefaults.buttonColors(containerColor = RecorderOrange),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (analyzing) "Listening…" else "Run analysis",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            when {
                analyzing -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RecorderOrange)
                }
                profile != null -> ProfileCard(profile!!)
                else -> Text(
                    "Tap Run analysis to start.",
                    color = RecorderBlueGrey,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(p: RoomProfile) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Quality stars
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quality", color = RecorderBlueGrey, fontSize = 11.sp)
            Text(
                "★".repeat(p.qualityStars) + "☆".repeat(5 - p.qualityStars),
                color = RecorderYellow,
                fontSize = 18.sp,
            )
        }
        Metric("Noise floor", "%.1f dB".format(p.noiseFloorDb), Color.White)
        Metric("RT60 estimate", "%.0f ms".format(p.rt60Ms), Color.White)
        Metric("Dominant freq", "%.0f Hz".format(p.dominantFreqHz), RecorderOrange)
    }
}

@Composable
private fun Metric(label: String, value: String, valueColor: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = RecorderBlueGrey, fontSize = 12.sp)
        Text(
            value,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            style = LocalAppTypography.current.numericMedium,
        )
    }
}
