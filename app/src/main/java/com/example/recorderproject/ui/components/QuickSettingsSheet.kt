package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecordingQuality
import com.example.recorderproject.ui.theme.AppTheme
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow

/**
 * Quick Settings bottom sheet — one-tap access to the most-used settings without
 * opening the full Settings page. Stateless: every value/lambda is supplied by the
 * caller (RecorderApp). Mirrors the app's other ModalBottomSheets (AudioSourcePicker,
 * EQPresetPicker). The full Settings page stays the source of truth; "Full Settings ›"
 * jumps there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsSheet(
    agcOn: Boolean,
    onToggleAgc: () -> Unit,
    hiPassOn: Boolean,
    onToggleHiPass: () -> Unit,
    antiClipOn: Boolean,
    onToggleAntiClip: () -> Unit,
    compressorOn: Boolean,
    onToggleCompressor: () -> Unit,
    stereoWidenerOn: Boolean,
    onToggleStereoWidener: () -> Unit,
    vadOn: Boolean,
    onToggleVad: () -> Unit,
    quality: RecordingQuality,
    onChangeQuality: (RecordingQuality) -> Unit,
    isRecording: Boolean,
    currentTheme: AppTheme,
    onChangeTheme: (AppTheme) -> Unit,
    saveLocationLabel: String,
    onPickSaveLocation: () -> Unit,
    cloudBackupOn: Boolean,
    onToggleCloudBackup: () -> Unit,
    onOpenFullSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RecorderCharcoal,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "QUICK SETTINGS",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    "Full Settings ›",
                    color = RecorderOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenFullSettings)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            SheetSectionLabel("LIVE PROCESSING")
            // 6 toggles in a 2-column grid
            val toggles = listOf(
                Triple("AGC", agcOn, onToggleAgc),
                Triple("Hi-pass", hiPassOn, onToggleHiPass),
                Triple("Anti-clip", antiClipOn, onToggleAntiClip),
                Triple("Compressor", compressorOn, onToggleCompressor),
                Triple("Widener", stereoWidenerOn, onToggleStereoWidener),
                Triple("VAD", vadOn, onToggleVad),
            )
            toggles.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { (label, value, onToggle) ->
                        QuickToggle(label = label, value = value, onToggle = onToggle, modifier = Modifier.weight(1f))
                    }
                    if (rowItems.size == 1) Box(Modifier.weight(1f))
                }
            }

            SheetSectionLabel("QUALITY")
            QualityChipRow(current = quality, enabled = !isRecording, onChange = onChangeQuality)
            if (isRecording) {
                Text(
                    "Quality can't change while recording",
                    color = RecorderBlueGrey,
                    fontSize = 11.sp,
                )
            }

            SheetSectionLabel("THEME")
            ThemeSwatchRow(current = currentTheme, onChange = onChangeTheme)

            SheetSectionLabel("STORAGE")
            StorageRows(
                saveLocationLabel = saveLocationLabel,
                onPickSaveLocation = onPickSaveLocation,
                cloudBackupOn = cloudBackupOn,
                onToggleCloudBackup = onToggleCloudBackup,
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(RecorderYellow),
        )
        Text(text, color = Color.White, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuickToggle(label: String, value: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(RecorderCharcoalCard)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Switch(
            checked = value,
            onCheckedChange = { if (it != value) onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RecorderOrange,
                uncheckedThumbColor = RecorderBlueGrey,
                uncheckedTrackColor = RecorderCharcoal,
            ),
        )
    }
}

@Composable
private fun QualityChipRow(current: RecordingQuality, enabled: Boolean, onChange: (RecordingQuality) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(RecorderCharcoal)
            .padding(3.dp)
            .alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RecordingQuality.entries.forEach { q ->
            val active = q == current
            Text(
                q.displayName,
                color = if (active) Color.White else RecorderBlueGrey,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (active) RecorderOrange else Color.Transparent)
                    .then(if (enabled) Modifier.clickable { onChange(q) } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ThemeSwatchRow(current: AppTheme, onChange: (AppTheme) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTheme.entries.forEach { t ->
            val active = t == current
            Box(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (active) RecorderOrange.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onChange(t) }
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                ColorDot(
                    background = t.background,
                    surface = t.surface,
                    surfaceElevated = t.surfaceElevated,
                    size = 34.dp,
                )
            }
        }
    }
}

@Composable
private fun StorageRows(
    saveLocationLabel: String,
    onPickSaveLocation: () -> Unit,
    cloudBackupOn: Boolean,
    onToggleCloudBackup: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RecorderCharcoalCard)
                .clickable(onClick = onPickSaveLocation)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Save location", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(saveLocationLabel, color = RecorderBlueGrey, fontSize = 11.sp)
            }
            Text("Change", color = RecorderOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RecorderCharcoalCard)
                .clickable(onClick = onToggleCloudBackup)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Cloud backup", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (cloudBackupOn) "Backing up to Google Drive" else "Tap to back up to Google Drive",
                    color = RecorderBlueGrey,
                    fontSize = 11.sp,
                )
            }
            Switch(
                checked = cloudBackupOn,
                onCheckedChange = { if (it != cloudBackupOn) onToggleCloudBackup() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = RecorderOrange,
                    uncheckedThumbColor = RecorderBlueGrey,
                    uncheckedTrackColor = RecorderCharcoal,
                ),
            )
        }
    }
}
