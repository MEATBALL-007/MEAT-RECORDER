package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.recorderproject.ui.components.BrandWordmark
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * Full settings screen — Phase A port-back of old MEATrec settings UI.
 *
 * Categories (in order):
 * - APPEARANCE: theme picker (Phase B will swap for 10-theme grid)
 * - RECORDING: sample rate, bit depth, channel count, noise reduction
 * - TIMING: countdown, max duration auto-stop
 * - STORAGE: save location picker
 * - ACCESSIBILITY: reduce motion
 * - ABOUT: brand block + version
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenV2(
    viewModel: RecorderViewModel,
    theme: String,
    reduceMotion: Boolean,
    onChangeTheme: (String) -> Unit,
    onToggleReduceMotion: (Boolean) -> Unit,
    onPickSaveLocation: () -> Unit,
    onBack: () -> Unit,
) {
    val sampleRate by viewModel.sampleRate.collectAsStateWithLifecycle()
    val bitDepth by viewModel.bitDepth.collectAsStateWithLifecycle()
    val channelCount by viewModel.channelCount.collectAsStateWithLifecycle()
    val noiseReductionEnabled by viewModel.noiseReductionEnabled.collectAsStateWithLifecycle()
    val countdownSeconds by viewModel.countdownSeconds.collectAsStateWithLifecycle()
    val maxDurationMinutes by viewModel.maxDurationMinutes.collectAsStateWithLifecycle()
    val saveDirectoryUri by viewModel.saveDirectoryUri.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader("APPEARANCE")
            ThemeRow(theme, onChangeTheme)

            SectionHeader("RECORDING")
            ChipRow(
                label = "Sample rate",
                options = listOf(44100 to "44.1 k", 48000 to "48 k", 96000 to "96 k"),
                current = sampleRate,
                onChange = { viewModel.updateSampleRate(it) },
            )
            ChipRow(
                label = "Bit depth",
                options = listOf(16 to "16-bit", 24 to "24-bit", 32 to "32-float"),
                current = bitDepth,
                onChange = { viewModel.updateBitDepth(it) },
            )
            ChipRow(
                label = "Channels",
                options = listOf(1 to "Mono", 2 to "Stereo"),
                current = channelCount,
                onChange = { viewModel.updateChannelCount(it) },
            )
            ToggleRow(
                label = "Auto noise reduction",
                detail = "Runs noise gate after each take",
                value = noiseReductionEnabled,
                onChange = { viewModel.toggleNoiseReduction(it) },
            )

            SectionHeader("TIMING")
            ChipRow(
                label = "Pre-roll countdown",
                options = listOf(0 to "Off", 3 to "3 s", 5 to "5 s", 10 to "10 s"),
                current = countdownSeconds,
                onChange = { viewModel.updateCountdownSeconds(it) },
            )
            ChipRow(
                label = "Auto-stop after",
                options = listOf(0 to "Off", 5 to "5 m", 10 to "10 m", 30 to "30 m", 60 to "1 h"),
                current = maxDurationMinutes,
                onChange = { viewModel.updateMaxDurationMinutes(it) },
            )

            SectionHeader("STORAGE")
            SaveLocationRow(
                uri = saveDirectoryUri?.toString(),
                onPick = onPickSaveLocation,
            )

            SectionHeader("ACCESSIBILITY")
            ToggleRow(
                label = "Reduce motion",
                detail = "Disables splash intro and non-essential animations",
                value = reduceMotion,
                onChange = onToggleReduceMotion,
            )

            SectionHeader("ABOUT")
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RecorderCharcoalCard)
                    .padding(16.dp),
            ) {
                Column {
                    BrandWordmark(style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.5.sp,
                    ))
                    Text("Audio · field-grade · live EQ", color = RecorderBlueGrey, fontSize = 12.sp)
                    Text("Version 1.0 · Phase 1+", color = RecorderBlueGrey, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        color = RecorderBlueGrey,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun ToggleRow(label: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(detail, color = RecorderBlueGrey, fontSize = 11.sp)
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RecorderOrange,
                uncheckedThumbColor = RecorderBlueGrey,
                uncheckedTrackColor = Color(0xFF0C0C10),
            ),
        )
    }
}

@Composable
private fun ThemeRow(current: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Theme", color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF0C0C10)).padding(3.dp),
        ) {
            for (t in listOf("MEATrec", "Light", "System")) {
                val active = t == current
                Text(
                    t,
                    color = if (active) Color.White else RecorderBlueGrey,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (active) RecorderOrange else Color.Transparent)
                        .clickable { onChange(t) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Generic chip-row picker: label on the left, segmented chip group on the right.
 * Each chip is one [Int] option with a [String] display label.
 */
@Composable
private fun <T> ChipRow(
    label: String,
    options: List<Pair<T, String>>,
    current: T,
    onChange: (T) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF0C0C10)).padding(3.dp),
        ) {
            for ((value, displayLabel) in options) {
                val active = value == current
                Text(
                    displayLabel,
                    color = if (active) Color.White else RecorderBlueGrey,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (active) RecorderOrange else Color.Transparent)
                        .clickable { onChange(value) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SaveLocationRow(uri: String?, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .clickable { onPick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = RecorderYellow, modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Save location", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                text = uri ?: "Default app folder · tap to change",
                color = RecorderBlueGrey,
                fontSize = 11.sp,
            )
        }
    }
}
