package com.example.recorderproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AssignmentLate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recorderproject.RecorderViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.recorderproject.ui.components.AudioSourcePicker
import com.example.recorderproject.ui.components.BitDepthSelector
import com.example.recorderproject.ui.components.CircleRecordButton
import com.example.recorderproject.ui.components.GainSlider
import com.example.recorderproject.ui.components.MeatrecMark
import com.example.recorderproject.ui.components.PreRecordInputsCard
import com.example.recorderproject.ui.components.MonitorLevelMeter
import com.example.recorderproject.ui.components.RecorderFeatureChips
import com.example.recorderproject.ui.components.formatElapsed
import com.example.recorderproject.ui.components.RecordingFileList
import com.example.recorderproject.ui.components.RecordingMeterBar
import com.example.recorderproject.ui.components.SampleRateSelector
import com.example.recorderproject.ui.components.SpectrumSplash
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn as fadeInAnim
import androidx.compose.animation.fadeOut as fadeOutAnim
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderOrange
import com.example.recorderproject.ui.theme.RecorderYellow
import com.example.recorderproject.ui.components.BrandWordmark
import com.example.recorderproject.ui.components.SortPicker
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderApp(
    viewModel: RecorderViewModel,
    onStartRecording: () -> Unit,
    onSelectSaveLocation: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenEQOnLast: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val files by viewModel.visibleRecordFiles.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val fileFilter by viewModel.fileFilter.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedFileIds.collectAsStateWithLifecycle()
    // G1 playback bar state
    val selectedPlayFile by viewModel.selectedFile.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isPlayerReady by viewModel.isPlayerReady.collectAsStateWithLifecycle()
    val playPosMs by viewModel.currentPlaybackPosition.collectAsStateWithLifecycle()
    val playDurMs by viewModel.playbackDuration.collectAsStateWithLifecycle()
    val playSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playLoop by viewModel.playbackLoop.collectAsStateWithLifecycle()
    val playVolume by viewModel.playbackVolume.collectAsStateWithLifecycle()
    var playerExpanded by remember { mutableStateOf(false) }
    // G2 live noise gate state
    val liveNoiseGateOn by viewModel.liveNoiseGateOn.collectAsStateWithLifecycle()
    val fileName by viewModel.fileName.collectAsStateWithLifecycle()
    val sampleRate by viewModel.sampleRate.collectAsStateWithLifecycle()
    val bitDepth by viewModel.bitDepth.collectAsStateWithLifecycle()
    val waveform by viewModel.currentWaveform.collectAsStateWithLifecycle()

    // Elapsed seconds tracker — increments while recording
    var elapsed by remember { mutableStateOf(0) }
    LaunchedEffect(isRecording) {
        elapsed = 0
        while (isRecording) {
            kotlinx.coroutines.delay(1000)
            elapsed++
        }
    }

    var slateOpen by remember { mutableStateOf(false) }
    var sourcePickerOpen by remember { mutableStateOf(false) }
    var splashTrigger by remember { mutableStateOf(0) }
    var editingFileName by remember { mutableStateOf(false) }
    val fileNameFocus = remember { FocusRequester() }
    val sceneName by viewModel.sceneName.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val monitorOn by viewModel.monitorEnabled.collectAsStateWithLifecycle()
    val monitorLevel by viewModel.monitorLevel.collectAsStateWithLifecycle()
    val liveEqOn by viewModel.liveEqEnabled.collectAsStateWithLifecycle()
    val micSource by viewModel.micSourceLabel.collectAsStateWithLifecycle()
    val inputGainDb by viewModel.inputGainDb.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MeatrecMark(size = 32.dp)
                        BrandWordmark()
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.openMenu() }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = RecorderYellow)
                    }
                },
                actions = {
                    IconButton(onClick = { slateOpen = true }) {
                        Icon(Icons.Outlined.AssignmentLate, contentDescription = "Slate", tint = RecorderBlueGrey)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = RecorderBlueGrey)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RecorderCharcoal),
            )
        },
        containerColor = RecorderCharcoal,
    ) { inner ->
        // Toggle between IDLE home content and RECORDING session view based on isRecording
        AnimatedContent(
            targetState = isRecording,
            transitionSpec = { fadeInAnim(tween(360)) togetherWith fadeOutAnim(tween(200)) },
            label = "recOrIdle",
            modifier = Modifier.fillMaxSize().padding(inner),
        ) { recording ->
            if (recording) {
                val cueCountState by viewModel.liveCueCount.collectAsStateWithLifecycle()
                RecordingSessionView(
                    fileName = fileName,
                    elapsedSeconds = elapsed,
                    levels = waveform,
                    sampleRate = sampleRate,
                    cueCount = cueCountState,
                    onDropCue = { viewModel.dropCueMarker() },
                    onStop = {
                        splashTrigger++
                        viewModel.stopRecording()
                    },
                )
            } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // H1: Hero record zone — dominant visual on the home screen
            com.example.recorderproject.ui.components.HeroRecordSection(
                isRecording = isRecording,
                elapsedSeconds = elapsed,
                fileName = fileName,
                sampleRateLabel = "${sampleRate / 1000} kHz",
                bitDepthLabel = "${bitDepth}-bit",
                recordButton = {
                    CircleRecordButton(
                        isRecording = isRecording,
                        onTap = {
                            splashTrigger++
                            if (isRecording) viewModel.stopRecording() else onStartRecording()
                        },
                    )
                },
            )

            // Compact status row — file name (tap to edit) + sample rate / bit depth selectors
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RecorderCharcoalCard)
                    .padding(Spacing.md),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("FILE", style = LocalAppTypography.current.labelTiny, color = RecorderBlueGrey)
                    // File name — clickable to enter edit mode (idle only); shows BasicTextField when editing
                    if (editingFileName && !isRecording) {
                        LaunchedEffect(Unit) { fileNameFocus.requestFocus() }
                        BasicTextField(
                            value = fileName,
                            onValueChange = { viewModel.updateFileName(it) },
                            textStyle = LocalTextStyle.current.copy(
                                color = Color_white(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(RecorderOrange),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { editingFileName = false }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(fileNameFocus),
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                fileName,
                                color = Color_white(),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (!isRecording)
                                            Modifier.clickable { editingFileName = true }
                                        else Modifier
                                    ),
                            )
                            if (!isRecording) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit file name",
                                    tint = RecorderBlueGrey,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (isRecording) "● REC" else "Idle",
                            color = if (isRecording) RecorderOrange else RecorderBlueGrey,
                            fontWeight = FontWeight.SemiBold,
                        )
                        SampleRateSelector(
                            current = sampleRate,
                            onChange = { viewModel.updateSampleRate(it) },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("BIT DEPTH", style = LocalAppTypography.current.labelTiny, color = RecorderBlueGrey)
                        BitDepthSelector(
                            current = bitDepth,
                            onChange = { viewModel.updateBitDepth(it) },
                        )
                    }
                }
            }

            // Input gain slider — drag from -12 to +24 dB, applies in real time
            GainSlider(
                valueDb = inputGainDb,
                onChange = { viewModel.updateInputGainDb(it) },
            )

            // Feature chips row: Monitor (BT earphone) · Live EQ · Mic source · NR gate
            RecorderFeatureChips(
                monitorOn = monitorOn,
                liveEqOn = liveEqOn,
                liveNoiseGateOn = liveNoiseGateOn,
                micSourceLabel = micSource,
                onToggleMonitor = { viewModel.toggleMonitor() },
                onToggleLiveEq = { viewModel.toggleLiveEq() },
                onToggleLiveNoiseGate = { viewModel.toggleLiveNoiseGate() },
                onOpenSourcePicker = { sourcePickerOpen = true },
            )

            // Pre-record input level meter — visible only when monitoring and not recording
            AnimatedVisibility(
                visible = monitorOn && !isRecording,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                MonitorLevelMeter(level = monitorLevel, modifier = Modifier.fillMaxWidth())
            }

            // Pre-record inputs — only shown when not currently recording
            AnimatedVisibility(
                visible = !isRecording,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                PreRecordInputsCard(
                    fileName = fileName,
                    sceneName = sceneName,
                    notes = notes,
                    enabled = !isRecording,
                    onFileNameChange = { viewModel.updateFileName(it) },
                    onSceneChange = { viewModel.updateSceneName(it) },
                    onNotesChange = { viewModel.updateNotes(it) },
                )
            }

            // Recording meter: appears with a fade+expand when recording starts
            AnimatedVisibility(
                visible = isRecording,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                val cueCount by viewModel.liveCueCount.collectAsStateWithLifecycle()
                val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
                RecordingMeterBar(
                    elapsedSeconds = elapsed,
                    levels = waveform,
                    cueCount = cueCount,
                    isPaused = isPaused,
                    onDropCue = { viewModel.dropCueMarker() },
                    onDropCueWithLabel = { viewModel.dropCueMarker(label = it) },
                    onTogglePause = { viewModel.togglePause() },
                )
            }

            // Secondary actions row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSelectSaveLocation, modifier = Modifier.weight(1f)) {
                    Text("Save folder", color = RecorderYellow, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onOpenEQOnLast,
                    enabled = files.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "Open EQ on last",
                        color = if (files.isNotEmpty()) RecorderYellow else RecorderBlueGrey,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // File list section header
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("RECORDINGS", style = LocalAppTypography.current.labelTiny, color = RecorderBlueGrey)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text("${files.size}", style = LocalAppTypography.current.labelTiny, color = RecorderYellow)
                    SortPicker(current = sortOrder, onChange = { viewModel.setSortOrder(it) })
                }
            }

            // F6: search bar
            com.example.recorderproject.ui.components.RecordingsSearchBar(
                query = searchQuery,
                onChange = { viewModel.setSearchQuery(it) },
            )

            // F7: filter chips
            com.example.recorderproject.ui.components.RecordingsFilterChips(
                current = fileFilter,
                onChange = { viewModel.setFileFilter(it) },
            )

            // F8: bulk-selection action bar — visible only when something is selected
            AnimatedVisibility(
                visible = selectedIds.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RecorderCharcoalCard)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${selectedIds.size} selected",
                        color = RecorderYellow,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            "Clear",
                            color = RecorderBlueGrey,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0C0C10))
                                .clickable { viewModel.clearSelection() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        Text(
                            "🗑 Delete",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RecorderOrange)
                                .clickable { viewModel.deleteSelected() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            val ctx = androidx.compose.ui.platform.LocalContext.current
            // G1: persistent mini player — appears when a file is selected for playback
            com.example.recorderproject.ui.components.MiniPlayerBar(
                file = selectedPlayFile,
                isPlaying = isPlaying,
                isReady = isPlayerReady,
                positionMs = playPosMs,
                durationMs = playDurMs,
                speed = playSpeed,
                loop = playLoop,
                volume = playVolume,
                onPlayPause = { viewModel.playPause() },
                onSeek = { viewModel.seekTo(it) },
                onClose = { viewModel.closePlayer() },
                onToggleLoop = { viewModel.toggleLoop() },
                onChangeSpeed = { viewModel.setPlaybackSpeed(it) },
                onChangeVolume = { viewModel.setPlaybackVolume(it) },
                expanded = playerExpanded,
                onToggleExpand = { playerExpanded = !playerExpanded },
            )

            RecordingFileList(
                files = files,
                onTapFile = {
                    viewModel.selectFile(it)
                    // Auto-play when user picks a file
                    playerExpanded = false
                },
                onTapEQ = { viewModel.onEQOpen(it) },
                onShare = { viewModel.shareRecording(it) },
                onDelete = { viewModel.deleteRecording(it) },
                onToggleLock = { viewModel.toggleLockRecording(it) },
                onToggleStar = { viewModel.toggleStarRecording(it) },
                onRename = { f, newName ->
                    val ok = viewModel.renameRecording(f, newName)
                    if (!ok) android.widget.Toast.makeText(
                        ctx, "Rename failed", android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onApplyNR = { viewModel.applyNoiseReduce(it) },
                // Phase C/D/E features — surface the menu items now so the UI is structurally
                // complete, but the actual screens land in later phases.
                onOpenSpectrogram = {
                    android.widget.Toast.makeText(ctx, "Spectrogram coming in Phase C", android.widget.Toast.LENGTH_SHORT).show()
                },
                onOpenPortrait = { viewModel.openPortrait(it) },
                onSliceScenes = { viewModel.openSceneSlicer(it) },
                onSetGhostTake = {
                    viewModel.setGhostTake(it)
                    android.widget.Toast.makeText(ctx, "Ghost take set: ${it.name}", android.widget.Toast.LENGTH_SHORT).show()
                },
                onDetectSync = {
                    val ms = viewModel.detectSyncPoint(it)
                    val msg = if (ms != null) "Sync at ${formatElapsed((ms / 1000L).toInt())} (${ms} ms)" else "No onset found"
                    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
                },
                onPitchShift = { viewModel.openPitchShift(it) },
                selectedIds = selectedIds,
                onToggleSelect = { viewModel.toggleFileSelection(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
            } // end else (idle home)
        }
    }

    // Spectrum-splash overlay — erupts when Record/Stop is pressed
    SpectrumSplash(
        triggerKey = splashTrigger,
        modifier = Modifier.fillMaxSize(),
    )
    } // end outer Box

    if (sourcePickerOpen) {
        AudioSourcePicker(
            currentSourceName = micSource,
            usbDevices = emptyList(),
            onPickBuiltin = { viewModel.setMicSource(it) },
            onPickUsb = { viewModel.setMicSource(it.productName) },
            onDismiss = { sourcePickerOpen = false },
        )
    }

    if (slateOpen) {
        com.example.recorderproject.ui.components.ProductionSlateDialog(
            initialScene = sceneName,
            initialTake = "",
            initialRoll = "",
            initialCamera = "",
            initialNotes = notes,
            onConfirm = { scene, _, _, _, n ->
                viewModel.updateSceneName(scene)
                viewModel.updateNotes(n)
            },
            onDismiss = { slateOpen = false },
        )
    }
}

// Small helper because using Color.White directly in many places adds verbosity
@Composable
private fun Color_white(): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White
