package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.RecorderViewModel

@Composable
fun AbCompareScreen(viewModel: RecorderViewModel, onBack: () -> Unit) {
    val files by viewModel.abFiles.collectAsStateWithLifecycle()
    val slot by viewModel.abPlayingSlot.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFA4616), Color(0xFFE13606)),
                    ),
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("A/B Compare", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Pick the best take", color = Color(0xFFFFC72C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        val pair = files ?: return@Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AbCard(
                label = "TAKE A",
                fileName = pair.first.name,
                durationSeconds = pair.first.durationSeconds,
                isPlaying = slot == 1,
                onPlay = { viewModel.abPlay(1) },
                onStop = { viewModel.abStop() },
            )
            AbCard(
                label = "TAKE B",
                fileName = pair.second.name,
                durationSeconds = pair.second.durationSeconds,
                isPlaying = slot == 2,
                onPlay = { viewModel.abPlay(2) },
                onStop = { viewModel.abStop() },
            )
            Text(
                "Tip: tap A then B (or B then A) to flip between takes and judge which sounds better.",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun AbCard(
    label: String,
    fileName: String,
    durationSeconds: Int,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {
    val bg = if (isPlaying) Color(0xFFFA4616) else Color(0xFF161616)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = if (isPlaying) Color.White else Color(0xFFFFC72C), fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            Text(fileName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("${durationSeconds}s", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = if (isPlaying) 0.20f else 0.10f))
                .clickable(onClick = if (isPlaying) onStop else onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Stop" else "Play",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
