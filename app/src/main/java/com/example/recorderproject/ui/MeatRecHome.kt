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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.ui.components.IconLineSettings

/**
 * MeatRec home screen — pixel-faithful rebuild of the 22 May APK home.
 *
 * Reference: recovery/screenshots/04-after-skip.png (idle) + 05/06/07-home-scroll
 *
 * Layout top → bottom:
 *   1. Orange filled top bar — "MeatRec" white bold + "Field Recording System"
 *      yellow subtitle + settings gear right
 *   2. Black body, scrollable column
 *   3. Big orange circle record button (240dp) with white center dot OR white
 *      square (stop) while recording
 *   4. "Tap to Record" + "Tap the button to start recording" labels
 *   5. "Recording Settings" expandable card with: File Name / Scene Name / Notes
 *      / NR toggle / Sample Rate pills / Channel Mode / Bit Depth / Audio Source
 *      / Save Location / Recording Limit / BT Monitor / Ghost Take / Room Profile
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeatRecHome(
    isRecording: Boolean,
    fileName: String,
    sceneName: String,
    notes: String,
    noiseReductionEnabled: Boolean,
    sampleRate: Int,
    bitDepth: Int,
    channelCount: Int,
    onFileNameChange: (String) -> Unit,
    onSceneNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onToggleNR: (Boolean) -> Unit,
    onChangeSampleRate: (Int) -> Unit,
    onChangeBitDepth: (Int) -> Unit,
    onChangeChannelCount: (Int) -> Unit,
    onTapRecord: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSourcePicker: () -> Unit = {},
    onPickSaveLocation: () -> Unit = {},
    onAnalyzeRoom: () -> Unit = {},
    files: List<RecordFile> = emptyList(),
    onTapFile: (RecordFile) -> Unit = {},
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ============== 1. ORANGE TOP BAR ==============
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MeatOrange)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "MeatRec",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                )
                Text(
                    "Field Recording System",
                    color = MeatYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                IconLineSettings(tint = Color.White, size = 26.dp)
            }
        }

        // ============== 2. SCROLLABLE BODY ==============
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero record button + labels
            BigRecordButton(isRecording = isRecording, onTap = onTapRecord)
            Text(
                if (isRecording) "Recording…" else "Tap to Record",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (isRecording) "Tap the button to stop recording" else "Tap the button to start recording",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 14.sp,
            )

            Box(modifier = Modifier.height(8.dp))

            // Recording Settings card
            RecordingSettingsCard(
                fileName = fileName,
                sceneName = sceneName,
                notes = notes,
                noiseReductionEnabled = noiseReductionEnabled,
                sampleRate = sampleRate,
                bitDepth = bitDepth,
                channelCount = channelCount,
                onFileNameChange = onFileNameChange,
                onSceneNameChange = onSceneNameChange,
                onNotesChange = onNotesChange,
                onToggleNR = onToggleNR,
                onChangeSampleRate = onChangeSampleRate,
                onChangeBitDepth = onChangeBitDepth,
                onChangeChannelCount = onChangeChannelCount,
                onOpenSourcePicker = onOpenSourcePicker,
                onPickSaveLocation = onPickSaveLocation,
                onAnalyzeRoom = onAnalyzeRoom,
            )
        }
    }
}

@Composable
private fun BigRecordButton(isRecording: Boolean, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(110.dp))
            .background(MeatOrange)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingSettingsCard(
    fileName: String,
    sceneName: String,
    notes: String,
    noiseReductionEnabled: Boolean,
    sampleRate: Int,
    bitDepth: Int,
    channelCount: Int,
    onFileNameChange: (String) -> Unit,
    onSceneNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onToggleNR: (Boolean) -> Unit,
    onChangeSampleRate: (Int) -> Unit,
    onChangeBitDepth: (Int) -> Unit,
    onChangeChannelCount: (Int) -> Unit,
    onOpenSourcePicker: () -> Unit,
    onPickSaveLocation: () -> Unit,
    onAnalyzeRoom: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(width = 4.dp, height = 18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MeatOrange),
                )
                Text(
                    "Recording Settings",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = if (expanded) "▲" else "▼",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 14.sp,
            )
        }

        if (expanded) {
            // File Name
            OutlinedFieldRow(
                label = "File Name",
                value = fileName,
                onChange = onFileNameChange,
            )
            // Scene Name
            OutlinedFieldRow(
                label = "Scene Name",
                value = sceneName,
                onChange = onSceneNameChange,
            )
            // Notes
            OutlinedFieldRow(
                label = "Notes (optional)",
                value = notes,
                onChange = onNotesChange,
                singleLine = false,
                minHeight = 100.dp,
            )

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

            // Noise Reduction
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Noise Reduction", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Applied after recording (local files only)",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = noiseReductionEnabled,
                    onCheckedChange = onToggleNR,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MeatOrange,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                        uncheckedTrackColor = Color(0xFF2A2A2A),
                    ),
                )
            }

            // Sample Rate
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sample Rate", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                PillRow(
                    options = listOf(
                        Triple(44100, "44.1 kHz", "CD"),
                        Triple(48000, "48 kHz", "Studio"),
                        Triple(96000, "96 kHz", "Hi-Res"),
                    ),
                    current = sampleRate,
                    onSelect = onChangeSampleRate,
                )
            }

            // Channel Mode
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Channel Mode", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (channelCount == 1) "MONO" else "STEREO",
                        color = MeatYellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33FFC72C))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                PillRow(
                    options = listOf(
                        Triple(1, "Mono", "VM40 Ch.1"),
                        Triple(2, "Stereo", "VM40 Dual"),
                    ),
                    current = channelCount,
                    onSelect = onChangeChannelCount,
                )
            }

            // Bit Depth
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bit Depth", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                PillRow(
                    options = listOf(
                        Triple(16, "16-bit", "PCM"),
                        Triple(24, "24-bit", "PCM"),
                        Triple(32, "32-bit", "Float"),
                    ),
                    current = bitDepth,
                    onSelect = onChangeBitDepth,
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

            // Audio Source
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Audio Source", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                OutlinedActionButton(label = "Microphone", onClick = onOpenSourcePicker)
            }

            // Save Location
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Save Location", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Default (app storage)", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                }
                OutlinedActionButton(label = "Browse", onClick = onPickSaveLocation)
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

            // Room Profile
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Room Profile", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Measures noise floor & reverb (2 sec)",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                    )
                }
                OutlinedActionButton(label = "Analyze", onClick = onAnalyzeRoom)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutlinedFieldRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.60f)) },
        singleLine = singleLine,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (minHeight > 0.dp) Modifier.height(minHeight) else Modifier),
        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = MeatOrange,
            unfocusedIndicatorColor = Color.White.copy(alpha = 0.20f),
            focusedLabelColor = MeatOrange,
            unfocusedLabelColor = Color.White.copy(alpha = 0.45f),
            cursorColor = MeatOrange,
        ),
    )
}

@Composable
private fun <T> PillRow(
    options: List<Triple<T, String, String>>,
    current: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1F1F1F))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((value, label, sublabel) in options) {
            val active = value == current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) MeatOrange else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = if (active) Color.White else Color.White.copy(alpha = 0.55f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    sublabel,
                    color = if (active) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun OutlinedActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 1.dp, vertical = 1.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Transparent),
        ) {
            Text(
                label,
                color = MeatOrange,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

// Brand colors — pinned from the 22 May APK screenshots, not from any theme indirection
internal val MeatOrange = Color(0xFFFA4616)
internal val MeatYellow = Color(0xFFFFC72C)
internal val CardBg = Color(0xFF161616)
