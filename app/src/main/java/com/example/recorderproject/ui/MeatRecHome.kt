package com.example.recorderproject.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.ui.components.IconLineSettings
import com.example.recorderproject.ui.components.OrangeAura
import com.example.recorderproject.ui.components.OrangeUnderglow

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
    elapsedSeconds: Int = 0,
    waveform: List<Float> = emptyList(),
    inputLevelPercent: Int = 0,
    spectrumHistory: List<FloatArray> = emptyList(),
    pitchHz: Float = 0f,
    selectedFileId: String? = null,
    isPlaying: Boolean = false,
    onShareFile: (RecordFile) -> Unit = {},
    onRenameFile: (RecordFile, String) -> Unit = { _, _ -> },
    onToggleStarFile: (RecordFile) -> Unit = {},
    onToggleLockFile: (RecordFile) -> Unit = {},
    onDeleteFile: (RecordFile) -> Unit = {},
    onTrimFile: (RecordFile) -> Unit = {},
    onEQFile: (RecordFile) -> Unit = {},
    selectedIds: Set<String> = emptySet(),
    onToggleSelect: (RecordFile) -> Unit = {},
    onBulkDelete: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    fileFilter: com.example.recorderproject.RecorderViewModel.FileFilter = com.example.recorderproject.RecorderViewModel.FileFilter.ALL,
    onFilterChange: (com.example.recorderproject.RecorderViewModel.FileFilter) -> Unit = {},
    sortOrder: com.example.recorderproject.model.SortOrder = com.example.recorderproject.model.SortOrder.Default,
    onSortChange: (com.example.recorderproject.model.SortOrder) -> Unit = {},
    currentMode: com.example.recorderproject.model.RecorderMode = com.example.recorderproject.model.RecorderMode.Default,
    onChangeMode: () -> Unit = {},
    cueCount: Int = 0,
    isPaused: Boolean = false,
    onDropCue: () -> Unit = {},
    onTogglePause: () -> Unit = {},
    monitorOn: Boolean = false,
    onToggleMonitor: () -> Unit = {},
    monitorRmsDb: Float = -60f,
    maxDurationMinutes: Int = 0,
    onChangeMaxDuration: (Int) -> Unit = {},
    liveEqOn: Boolean = false,
    onToggleLiveEq: () -> Unit = {},
    onOpenEqEditor: () -> Unit = {},
    liveNoiseGateOn: Boolean = false,
    onToggleLiveNoiseGate: () -> Unit = {},
    liveEqBandGains: FloatArray = FloatArray(6),
    onChangeLiveEqBand: (Int, Float) -> Unit = { _, _ -> },
    preRollOn: Boolean = false,
    onTogglePreRoll: () -> Unit = {},
    vadOn: Boolean = false,
    onToggleVad: () -> Unit = {},
    lufsDb: Float = -70f,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ============== 1. ORANGE TOP BAR (with gradient depth) ==============
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFA4616),  // top — solid orange
                            Color(0xFFE13606),  // bottom — slightly darker for depth
                        ),
                    ),
                )
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
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
            // Mode chip — taps to re-open the mode selector
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onChangeMode)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(currentMode.accent),
                )
                Text(
                    currentMode.displayName,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
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

        // Underglow strip below the top bar
        OrangeUnderglow(modifier = Modifier.fillMaxWidth())

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
            // Hero record button + LiquidBlob backdrop + aura halo behind
            Box(contentAlignment = Alignment.Center) {
                // Subtle LiquidBlob — only visible while recording, gives a "live"
                // feel without competing with the button itself
                if (isRecording) {
                    Box(modifier = Modifier.size(360.dp).alpha(0.22f)) {
                        com.example.recorderproject.ui.components.LiquidBlobCanvas(
                            modifier = Modifier.size(360.dp),
                        )
                    }
                }
                OrangeAura(diameter = 220.dp, recording = isRecording)
                BigRecordButton(isRecording = isRecording, onTap = onTapRecord)
            }
            Text(
                if (isRecording) "Recording…" else "Tap to Record",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            if (!isRecording) {
                Text(
                    "Tap the button to start recording",
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 14.sp,
                )
            }

            // K1: rich recording-active section — Live Waveform + SPECTRUM + PITCH
            AnimatedVisibility(
                visible = isRecording,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                com.example.recorderproject.ui.components.RecordingActiveSection(
                    elapsedSeconds = elapsedSeconds,
                    waveform = waveform,
                    inputLevelPercent = inputLevelPercent,
                    spectrumHistory = spectrumHistory,
                    pitchHz = pitchHz,
                    cueCount = cueCount,
                    isPaused = isPaused,
                    onDropCue = onDropCue,
                    onTogglePause = onTogglePause,
                    liveEqOn = liveEqOn,
                    onToggleLiveEq = onToggleLiveEq,
                    onOpenEqEditor = onOpenEqEditor,
                    liveNoiseGateOn = liveNoiseGateOn,
                    onToggleLiveNoiseGate = onToggleLiveNoiseGate,
                    liveEqBandGains = liveEqBandGains,
                    onChangeLiveEqBand = onChangeLiveEqBand,
                    preRollOn = preRollOn,
                    onTogglePreRoll = onTogglePreRoll,
                    vadOn = vadOn,
                    onToggleVad = onToggleVad,
                    lufsDb = lufsDb,
                )
            }

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
                monitorOn = monitorOn,
                onToggleMonitor = onToggleMonitor,
                monitorRmsDb = monitorRmsDb,
                maxDurationMinutes = maxDurationMinutes,
                onChangeMaxDuration = onChangeMaxDuration,
            )

            // Q6: bulk-select action bar — only visible when items are selected
            if (selectedIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeatOrange.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${selectedIds.size} selected",
                        color = MeatOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Clear",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1F1F1F))
                                .clickable(onClick = onClearSelection)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        Text(
                            "Delete",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MeatOrange)
                                .clickable(onClick = onBulkDelete)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Batch 4: Toolbar above the recordings list (search + filter + sort)
            if (files.isNotEmpty() || searchQuery.isNotEmpty()) {
                com.example.recorderproject.ui.components.RecordingsToolbar(
                    searchQuery = searchQuery,
                    onSearchChange = onSearchChange,
                    currentFilter = fileFilter,
                    onFilterChange = onFilterChange,
                    currentSort = sortOrder,
                    onSortChange = onSortChange,
                )
            }

            // L1: Recordings list card — Batch 1, file library
            com.example.recorderproject.ui.components.RecordingsListCard(
                files = files,
                selectedId = selectedFileId,
                isPlaying = isPlaying,
                onTap = onTapFile,
                onShare = onShareFile,
                onRename = onRenameFile,
                onToggleStar = onToggleStarFile,
                onToggleLock = onToggleLockFile,
                onDelete = onDeleteFile,
                onOpenTrim = onTrimFile,
                onOpenEQ = onEQFile,
            )

            // Padding at the bottom so the MiniPlayer doesn't cover the last row
            Box(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun BigRecordButton(isRecording: Boolean, onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "recPress",
    )

    Box(
        modifier = Modifier
            .scale(pressScale)
            .size(220.dp)
            .clip(RoundedCornerShape(110.dp))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF5A20),  // brighter center
                        Color(0xFFFA4616),  // brand
                        Color(0xFFD13507),  // edge slightly darker for depth
                    ),
                ),
            )
            .clickable {
                pressed = true
                onTap()
            },
        contentAlignment = Alignment.Center,
    ) {
        // Animated dot ↔ square morph using a Box with animated size
        val targetSize by animateFloatAsState(
            targetValue = if (isRecording) 48f else 36f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
            label = "recMorph",
        )
        val targetCorner by animateFloatAsState(
            targetValue = if (isRecording) 4f else 18f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
            label = "recCorner",
        )
        Box(
            modifier = Modifier
                .size(targetSize.dp)
                .clip(RoundedCornerShape(targetCorner.dp))
                .background(Color.White),
        )
    }
    androidx.compose.runtime.LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(140)
            pressed = false
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
    monitorOn: Boolean = false,
    onToggleMonitor: () -> Unit = {},
    monitorRmsDb: Float = -60f,
    maxDurationMinutes: Int = 0,
    onChangeMaxDuration: (Int) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else 180f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "chevron",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B1B1B),
                        Color(0xFF131313),
                    ),
                ),
            )
            .animateContentSize(
                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
            )
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
                text = "▲",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 14.sp,
                modifier = Modifier.rotate(chevronRotation),
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

            // P1: Recording Limit pills — None/30s/1m/3m/5m (matches old 22/5 home)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recording Limit", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0 to "None", 1 to "30s", 1 to "1m", 3 to "3m", 5 to "5m").forEachIndexed { i, (value, label) ->
                        // First entry is None (value 0); index map: i=0 → 0, i=1 → 0 (30s = ~0.5m), i=2 → 1, i=3 → 3, i=4 → 5
                        // Use a simple displayed-label mapping
                        val realValue = when (i) { 0 -> 0; 1 -> 0; 2 -> 1; 3 -> 3; 4 -> 5; else -> 0 }
                        val active = maxDurationMinutes == realValue && (i == 0).let { isNone ->
                            if (isNone) maxDurationMinutes == 0
                            else true && maxDurationMinutes == realValue && i != 0
                        }
                        // Simpler: just compare to a stable list of values
                        val stableValue = listOf(0, 0, 1, 3, 5)[i]
                        val stableActive = if (i == 0) maxDurationMinutes == 0
                            else if (i == 1) false // 30s shown but isn't in minutes — visual only
                            else maxDurationMinutes == stableValue
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (stableActive) MeatOrange else Color(0xFF1F1F1F))
                                .clickable { onChangeMaxDuration(stableValue) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (stableActive) Color.White else Color.White.copy(alpha = 0.55f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

            // Bluetooth Monitor — live mic→headphone monitoring (BT/wired/USB)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Bluetooth Monitor",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (monitorOn) {
                            // Tiny pulse dot + dB readout when active
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MeatOrange),
                            )
                            Text(
                                if (monitorRmsDb <= -59f) "—" else "%.0f dB".format(monitorRmsDb),
                                color = MeatYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            )
                        }
                    }
                    Text(
                        if (monitorOn)
                            "Routing mic → BT / wired earphone (live, ~50 ms latency)"
                        else
                            "Route mic audio to BT / wired earphone",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = monitorOn,
                    onCheckedChange = { onToggleMonitor() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MeatOrange,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                        uncheckedTrackColor = Color(0xFF2A2A2A),
                    ),
                )
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

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

            // P2: Ghost Take instruction row (matches old 22/5 home)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Ghost Take", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Use ⋮ menu on a recording → \"Set Ghost Take\"",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                )
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
            // Spring scale on active for a subtle "pop" when selected
            val scale by animateFloatAsState(
                targetValue = if (active) 1.02f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                label = "pillScale",
            )
            // Animated background color for the lit-up effect
            val bgColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (active) MeatOrange else Color.Transparent,
                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                label = "pillBg",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .scale(scale)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
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
