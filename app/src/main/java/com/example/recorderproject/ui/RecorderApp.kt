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
    AppleHomeScreen(
        files = files,
        isRecording = isRecording,
        elapsedSeconds = elapsed,
        searchQuery = searchQuery,
        onSearchChange = { viewModel.setSearchQuery(it) },
        selectedFile = selectedPlayFile,
        isPlaying = isPlaying,
        onTapFile = {
            viewModel.selectFile(it)
            playerExpanded = false
        },
        onStartRecording = {
            splashTrigger++
            onStartRecording()
        },
        onStopRecording = {
            splashTrigger++
            viewModel.stopRecording()
        },
        onExpandRecording = { /* TODO: open full recording sheet (Phase H+1) */ },
        onOpenMenu = { viewModel.openMenu() },
        onOpenSettings = onOpenSettings,
    )

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
