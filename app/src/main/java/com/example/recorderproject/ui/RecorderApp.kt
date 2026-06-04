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
    onOpenPresets: () -> Unit = {},
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

    // D: surface delivery-render result as a Toast
    val lastDelivery by viewModel.lastDeliveryResult.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(lastDelivery) {
        val r = lastDelivery ?: return@LaunchedEffect
        val target = r.targetLufs ?: return@LaunchedEffect
        val msg = when {
            r.passed -> "Rendered %.0f LUFS · %.1f dBTP · PASS".format(target, r.truePeakDbtp)
            r.integratedLufs < -60f -> "Too quiet to normalize — re-record louder."
            else -> "Couldn't reach %.0f LUFS without clipping — try a lower target.".format(target)
        }
        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
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
    val externalInputDevices by viewModel.externalInputDevices.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()

    val channelCount by viewModel.channelCount.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize()) {
    // Faithful rebuild of 22 May APK home — see recovery/screenshots/04-after-skip.png
    MeatRecHome(
        isRecording = isRecording,
        fileName = fileName,
        sceneName = sceneName,
        notes = notes,
        noiseReductionEnabled = viewModel.noiseReductionEnabled.collectAsStateWithLifecycle().value,
        sampleRate = sampleRate,
        bitDepth = bitDepth,
        channelCount = channelCount,
        onFileNameChange = { viewModel.updateFileName(it) },
        onSceneNameChange = { viewModel.updateSceneName(it) },
        onNotesChange = { viewModel.updateNotes(it) },
        onToggleNR = { viewModel.toggleNoiseReduction(it) },
        onChangeSampleRate = { viewModel.updateSampleRate(it) },
        onChangeBitDepth = { viewModel.updateBitDepth(it) },
        onChangeChannelCount = { viewModel.updateChannelCount(it) },
        onTapRecord = {
            splashTrigger++
            if (isRecording) viewModel.stopRecording() else onStartRecording()
        },
        onOpenSettings = onOpenSettings,
        isPro = isPro,
        onTapUpgrade = { viewModel.openPaywall(com.example.recorderproject.billing.ProFeature.HIGH_RES_AUDIO) },
        onOpenSourcePicker = { sourcePickerOpen = true },
        onPickSaveLocation = onSelectSaveLocation,
        onAnalyzeRoom = { viewModel.openRoomProfiler() },
        files = files,
        onTapFile = { viewModel.selectFile(it) },
        elapsedSeconds = elapsed,
        waveform = waveform,
        inputLevelPercent = if (isRecording && waveform.isNotEmpty()) {
            // Derive level from live recording samples (more responsive than monitor)
            val recent = waveform.takeLast(512)
            val rms = kotlin.math.sqrt(recent.map { (it * it).toDouble() }.average()).toFloat()
            (rms * 200f).toInt().coerceIn(0, 100)
        } else {
            (monitorLevel.rmsDb + 60f).coerceIn(0f, 60f).let { (it / 60f * 100f).toInt() }
        },
        spectrumHistory = viewModel.spectrumHistory.collectAsStateWithLifecycle().value,
        pitchHz = viewModel.livePitchHz.collectAsStateWithLifecycle().value,
        selectedFileId = selectedPlayFile?.id,
        isPlaying = isPlaying,
        onShareFile = { viewModel.shareRecording(it) },
        onRenameFile = { f, newName -> viewModel.renameRecording(f, newName) },
        onToggleStarFile = { viewModel.toggleStarRecording(it) },
        onToggleLockFile = { viewModel.toggleLockRecording(it) },
        onDeleteFile = { viewModel.deleteRecording(it) },
        onTrimFile = { viewModel.openTrim(it) },
        onEQFile = { viewModel.onEQOpen(it) },
        searchQuery = searchQuery,
        onSearchChange = { viewModel.setSearchQuery(it) },
        fileFilter = fileFilter,
        onFilterChange = { viewModel.setFileFilter(it) },
        sortOrder = sortOrder,
        onSortChange = { viewModel.setSortOrder(it) },
        currentMode = viewModel.recorderMode.collectAsStateWithLifecycle().value,
        onChangeMode = onOpenPresets,
        cueCount = viewModel.liveCueCount.collectAsStateWithLifecycle().value,
        isPaused = viewModel.isPaused.collectAsStateWithLifecycle().value,
        onDropCue = { viewModel.dropCueMarker() },
        onTogglePause = { viewModel.togglePause() },
        monitorOn = monitorOn,
        onToggleMonitor = { viewModel.toggleMonitor() },
        monitorRmsDb = monitorLevel.rmsDb,
        maxDurationMinutes = viewModel.maxDurationMinutes.collectAsStateWithLifecycle().value,
        onChangeMaxDuration = { viewModel.updateMaxDurationMinutes(it) },
        liveEqOn = liveEqOn,
        onToggleLiveEq = { viewModel.toggleLiveEq() },
        onOpenEqEditor = {
            val last = files.lastOrNull() ?: viewModel.recordFiles.value.lastOrNull()
            if (last != null) viewModel.onEQOpen(last)
        },
        liveNoiseGateOn = liveNoiseGateOn,
        onToggleLiveNoiseGate = { viewModel.toggleLiveNoiseGate() },
        liveEqBandGains = viewModel.liveEqBandGains.collectAsStateWithLifecycle().value,
        onChangeLiveEqBand = { band, gain -> viewModel.setLiveEqBand(band, gain) },
        preRollOn = viewModel.preRollEnabled.collectAsStateWithLifecycle().value,
        onTogglePreRoll = { viewModel.togglePreRoll() },
        vadOn = viewModel.vadOn.collectAsStateWithLifecycle().value,
        onToggleVad = { viewModel.toggleVad() },
        lufsDb = viewModel.liveLufs.collectAsStateWithLifecycle().value,
        liveTpDbtp = viewModel.liveTpDbTp.collectAsStateWithLifecycle().value,
        loudnessTarget = viewModel.loudnessTarget.collectAsStateWithLifecycle().value,
        onSelectLoudnessTarget = { viewModel.setSessionLoudnessTarget(it) },
        onSaveLoudnessAsDefault = { viewModel.saveAsDefaultLoudnessTarget(it) },
        liveRawPeakDbfs = viewModel.liveRawPeakDbfs.collectAsStateWithLifecycle().value,
        inputGainDb = inputGainDb,
        onChangeInputGain = { viewModel.updateInputGainDb(it) },
        onBumpTake = { viewModel.bumpTake(+1) },
        onBumpSubscene = { viewModel.bumpSubscene(+1) },
        onTakeMinus1 = { viewModel.bumpTake(-1) },
        onSceneMinus1 = { viewModel.bumpScene(-1) },
        onScenePlus1 = { viewModel.bumpScene(+1) },
        selectedIds = selectedIds,
        onToggleSelect = { viewModel.toggleFileSelection(it.id) },
        onBulkDelete = { viewModel.deleteSelected() },
        onClearSelection = { viewModel.clearSelection() },
        onBulkCompareAb = { viewModel.openAbCompareFromSelection() },
        onSlateTone = { viewModel.fireSlateTone() },
        micSource = micSource,
        phaseCorrelation = viewModel.phaseCorrelation.collectAsStateWithLifecycle().value,
    )

    // L2: Bottom mini player — pinned to bottom of the Box, slides up when a file is selected
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
    ) {
        com.example.recorderproject.ui.components.MeatRecMiniPlayer(
            file = selectedPlayFile,
            isPlaying = isPlaying,
            positionMs = playPosMs,
            durationMs = playDurMs,
            onPlayPause = { viewModel.playPause() },
            onSeek = { viewModel.seekTo(it) },
            onClose = { viewModel.closePlayer() },
        )
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
            externalDevices = externalInputDevices,
            onPickBuiltin = {
                viewModel.clearInputDevice()
                viewModel.setMicSource(it)
            },
            onPickExternal = { device ->
                viewModel.selectInputDevice(device)
            },
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
