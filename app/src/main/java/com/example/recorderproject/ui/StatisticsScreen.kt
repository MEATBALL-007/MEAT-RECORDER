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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.RecorderViewModel
import com.example.recorderproject.ui.components.formatElapsed
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * G16: Statistics screen — total time recorded, file counts, biggest file,
 * starred count, etc. Pure computation over current recordFiles list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: RecorderViewModel,
    onBack: () -> Unit,
) {
    val files by viewModel.recordFiles.collectAsStateWithLifecycle()
    val totalSeconds = files.sumOf { it.durationSeconds.toLong() }
    val starredCount = files.count { it.starred }
    val lockedCount = files.count { it.isLocked }
    val nrCount = files.count { it.hasNoiseReduction }
    val eqCount = files.count { it.hasEQ }
    val longest = files.maxByOrNull { it.durationSeconds }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
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
            BigStat("TOTAL RECORDED", formatElapsed(totalSeconds.toInt()), RecorderOrange)
            BigStat("RECORDINGS", files.size.toString(), RecorderYellow)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallStat("★ Starred", starredCount.toString(), Modifier.weight(1f))
                SmallStat("🔒 Locked", lockedCount.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallStat("NR processed", nrCount.toString(), Modifier.weight(1f))
                SmallStat("EQ applied", eqCount.toString(), Modifier.weight(1f))
            }

            if (longest != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RecorderCharcoalCard)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("LONGEST TAKE", color = RecorderBlueGrey, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    Text(longest.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(formatElapsed(longest.durationSeconds), color = RecorderYellow, style = LocalAppTypography.current.numericMedium)
                }
            }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = RecorderBlueGrey, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(value, color = accent, style = LocalAppTypography.current.numericLarge, fontWeight = FontWeight.Bold, fontSize = 36.sp)
    }
}

@Composable
private fun SmallStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = RecorderBlueGrey, fontSize = 10.sp)
        Text(value, color = Color.White, style = LocalAppTypography.current.numericMedium, fontWeight = FontWeight.Bold)
    }
}
