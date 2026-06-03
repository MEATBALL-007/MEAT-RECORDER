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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.recorderproject.ui.components.ColorDot
import com.example.recorderproject.ui.theme.AppTheme
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
    theme: AppTheme,
    reduceMotion: Boolean,
    onChangeTheme: (AppTheme) -> Unit,
    onToggleReduceMotion: (Boolean) -> Unit,
    onPickSaveLocation: () -> Unit,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit = {},
    onChangeMode: () -> Unit = {},
    onPickCloudLocation: () -> Unit = {},
    onSignInDrive: () -> Unit = {},
) {
    val sampleRate by viewModel.sampleRate.collectAsStateWithLifecycle()
    val bitDepth by viewModel.bitDepth.collectAsStateWithLifecycle()
    val channelCount by viewModel.channelCount.collectAsStateWithLifecycle()
    val noiseReductionEnabled by viewModel.noiseReductionEnabled.collectAsStateWithLifecycle()
    val countdownSeconds by viewModel.countdownSeconds.collectAsStateWithLifecycle()
    val maxDurationMinutes by viewModel.maxDurationMinutes.collectAsStateWithLifecycle()
    val saveDirectoryUri by viewModel.saveDirectoryUri.collectAsStateWithLifecycle()
    val agcOn by viewModel.agcOn.collectAsStateWithLifecycle()
    val hiPassOn by viewModel.hiPassOn.collectAsStateWithLifecycle()
    val antiClipOn by viewModel.antiClipOn.collectAsStateWithLifecycle()
    val vadOn by viewModel.vadOn.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()
    val compressorOn by viewModel.compressorOn.collectAsStateWithLifecycle()
    val stereoWidenerOn by viewModel.stereoWidenerOn.collectAsStateWithLifecycle()
    val cloudBackupOn by viewModel.cloudBackupOn.collectAsStateWithLifecycle()
    val lockScreenControlsOn by viewModel.lockScreenControlsOn.collectAsStateWithLifecycle()
    val groupByScene by viewModel.groupByScene.collectAsStateWithLifecycle()
    val autoStopMin by viewModel.autoStopMinutes.collectAsStateWithLifecycle()

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
            SectionHeader("APPEARANCE", accent = RecorderYellow)
            ThemeGrid(current = theme, onChange = onChangeTheme)

            SectionHeader("RECORDING", accent = RecorderOrange)
            // G8 quality preset chip row
            ChipRow(
                label = "Quality preset",
                options = com.example.recorderproject.model.RecordingQuality.entries.map { it to it.displayName },
                current = quality,
                onChange = { viewModel.setQuality(it) },
            )
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
            // G9 / G10 / G11 / G13 live DSP toggles
            ToggleRow(
                label = "Auto gain control (AGC)",
                detail = "Smooths input level toward target during record",
                value = agcOn,
                onChange = { viewModel.toggleAgc() },
            )
            ToggleRow(
                label = "Hi-pass filter (rumble cut)",
                detail = "Removes content below ~80 Hz",
                value = hiPassOn,
                onChange = { viewModel.toggleHiPass() },
            )
            ToggleRow(
                label = "Anti-clipping attenuator",
                detail = "Auto-drops gain when peak approaches 0 dBFS",
                value = antiClipOn,
                onChange = { viewModel.toggleAntiClip() },
            )
            ToggleRow(
                label = "Voice activation start (VAD)",
                detail = "Auto-arms recording when voice detected",
                value = vadOn,
                onChange = { viewModel.toggleVad() },
            )
            ToggleRow(
                label = "Compressor",
                detail = "Soft-limit transients during recording",
                value = compressorOn,
                onChange = { viewModel.toggleCompressor() },
            )
            ToggleRow(
                label = "Stereo widener",
                detail = "Mid/side widening (stereo recordings only)",
                value = stereoWidenerOn,
                onChange = { viewModel.toggleStereoWidener() },
            )
            ChipRow(
                label = "Auto-stop after",
                options = listOf(0 to "Off", 1 to "1 m", 5 to "5 m", 15 to "15 m", 30 to "30 m"),
                current = autoStopMin,
                onChange = { viewModel.setAutoStopMinutes(it) },
            )

            SectionHeader("ORGANIZATION", accent = Color(0xFF7B8189))
            ToggleRow(
                label = "Group recordings by scene",
                detail = "Visual folder grouping by sceneName tag",
                value = groupByScene,
                onChange = { viewModel.toggleGroupByScene() },
            )
            ToggleRow(
                label = "Cloud backup",
                detail = "Auto-upload to cloud (provider wiring later)",
                value = cloudBackupOn,
                onChange = { viewModel.toggleCloudBackup() },
            )
            ToggleRow(
                label = "Lock-screen controls",
                detail = "Show transport controls on lock screen",
                value = lockScreenControlsOn,
                onChange = { viewModel.toggleLockScreenControls() },
            )

            SectionHeader("TIMING", accent = Color(0xFF3DC399))
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

            SectionHeader("STORAGE", accent = RecorderYellow)
            SaveLocationRow(
                uri = saveDirectoryUri?.toString(),
                onPick = onPickSaveLocation,
            )

            SectionHeader("ACCESSIBILITY", accent = Color(0xFF7B8189))
            ToggleRow(
                label = "Reduce motion",
                detail = "Disables splash intro and non-essential animations",
                value = reduceMotion,
                onChange = onToggleReduceMotion,
            )

            SectionHeader("ABOUT", accent = RecorderOrange)
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1A20),
                                Color(0xFF111116),
                            ),
                        ),
                    )
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BrandWordmark(style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.5.sp,
                    ))
                    Text(
                        "Audio · field-grade · live EQ",
                        color = RecorderBlueGrey,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(RecorderYellow),
                        )
                        Text(
                            "Version 1.0 · 50+ features",
                            color = RecorderYellow,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenPrivacy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RecorderBlueGrey),
            ) {
                Text("Privacy Policy", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, accent: Color = RecorderOrange) {
    // iOS-style section header — small caps + a tiny accent bar.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Text(
            text,
            color = Color.White,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
        )
    }
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

/**
 * 10-theme picker grid — 2 columns × 5 rows. Each cell shows a [ColorDot] swatch
 * with the theme's bg/surface/surfaceElevated triple, theme name, and a halo
 * around the active selection (RecorderOrange border).
 */
@Composable
private fun ThemeGrid(current: AppTheme, onChange: (AppTheme) -> Unit) {
    val entries = AppTheme.entries
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 2-column grid — chunk into rows of 2
        entries.chunked(2).forEach { rowEntries ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowEntries.forEach { t ->
                    ThemeCell(
                        theme = t,
                        active = t == current,
                        onClick = { onChange(t) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // pad row when odd count (10 themes → always even, but defensive)
                if (rowEntries.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemeCell(
    theme: AppTheme,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (active) RecorderOrange else Color(0xFF2A2D33)
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(theme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ColorDot(
            background = theme.background,
            surface = theme.surface,
            surfaceElevated = theme.surfaceElevated,
            size = 26.dp,
        )
        Text(
            theme.displayName,
            color = if (active) RecorderYellow else Color.White,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (active) {
            Text("●", color = RecorderOrange, fontSize = 14.sp)
        }
        // Faint outline (using a border-like effect via the surface contrast)
        // — implemented as a separate Box layer would inflate complexity; the
        // active dot above is enough visual indication paired with the yellow text.
        @Suppress("UNUSED_VARIABLE") val unused = borderColor
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
