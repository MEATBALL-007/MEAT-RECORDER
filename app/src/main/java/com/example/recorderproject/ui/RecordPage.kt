package com.example.recorderproject.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The "Record" page of the home pager: hero record button, live recording-active
 * section, and the recording-settings card. Pure presentation — all state is hoisted.
 */
@Composable
fun RecordPage(
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
    onOpenSourcePicker: () -> Unit,
    onPickSaveLocation: () -> Unit,
    onAnalyzeRoom: () -> Unit,
    elapsedSeconds: Int,
    waveform: List<Float>,
    inputLevelPercent: Int,
    spectrumHistory: List<FloatArray>,
    pitchHz: Float,
    cueCount: Int,
    isPaused: Boolean,
    onDropCue: () -> Unit,
    onTogglePause: () -> Unit,
    liveEqOn: Boolean,
    onToggleLiveEq: () -> Unit,
    onOpenEqEditor: () -> Unit,
    liveNoiseGateOn: Boolean,
    onToggleLiveNoiseGate: () -> Unit,
    liveEqBandGains: FloatArray,
    onChangeLiveEqBand: (Int, Float) -> Unit,
    preRollOn: Boolean,
    onTogglePreRoll: () -> Unit,
    vadOn: Boolean,
    onToggleVad: () -> Unit,
    lufsDb: Float,
    liveTpDbtp: Float,
    loudnessTarget: com.example.recorderproject.model.LoudnessTarget,
    onSlateTone: () -> Unit,
    micSource: String,
    phaseCorrelation: Float,
    monitorOn: Boolean,
    onToggleMonitor: () -> Unit,
    monitorRmsDb: Float,
    maxDurationMinutes: Int,
    onChangeMaxDuration: (Int) -> Unit,
    liveRawPeakDbfs: Float,
    inputGainDb: Float,
    onChangeInputGain: (Float) -> Unit,
    onBumpTake: () -> Unit,
    onBumpSubscene: () -> Unit,
    onSubsceneMinus: () -> Unit,
    onTakeMinus1: () -> Unit,
    onSceneMinus1: () -> Unit,
    onScenePlus1: () -> Unit,
    onSelectLoudnessTarget: (com.example.recorderproject.model.LoudnessTarget) -> Unit,
    onSaveLoudnessAsDefault: (com.example.recorderproject.model.LoudnessTarget) -> Unit,
    workspaceLayout: com.example.recorderproject.model.WorkspaceLayout = com.example.recorderproject.model.WorkspaceLayout.DEFAULT,
    isProUser: Boolean = true,
    onUpgradeFeature: (com.example.recorderproject.billing.ProFeature) -> Unit = {},
    onOpenCustomize: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero record button + LiquidBlob backdrop + aura halo behind.
        // BoxWithConstraints lets the hero scale down on narrow phones so the
        // 220dp button / 360dp blob never clip, while capping size on tablets.
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val buttonSize = minOf(maxWidth * 0.6f, 220.dp)
            val blobSize   = minOf(maxWidth * 0.98f, 360.dp)
            // Subtle LiquidBlob — only visible while recording, gives a "live"
            // feel without competing with the button itself
            if (isRecording) {
                Box(modifier = Modifier.size(blobSize).alpha(0.22f)) {
                    com.example.recorderproject.ui.components.LiquidBlobCanvas(
                        modifier = Modifier.size(blobSize),
                    )
                }
            }
            com.example.recorderproject.ui.components.OrangeAura(diameter = buttonSize, recording = isRecording)
            BigRecordButton(isRecording = isRecording, onTap = onTapRecord, diameter = buttonSize)
        }
        Text(
            if (isRecording) "Recording…" else androidx.compose.ui.res.stringResource(com.example.recorderproject.R.string.tap_to_record),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
        if (!isRecording) {
            Text(
                androidx.compose.ui.res.stringResource(com.example.recorderproject.R.string.tap_to_start),
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 14.sp,
            )
        }

        // D: Loudness delivery target chip
        com.example.recorderproject.ui.components.LoudnessTargetChip(
            current = loudnessTarget,
            onSelectSession = onSelectLoudnessTarget,
            onSaveAsDefault = onSaveLoudnessAsDefault,
        )

        // G: Raw input level + gain — always visible, dial in before tapping record
        com.example.recorderproject.ui.components.InputGainCard(
            peakDbfs = liveRawPeakDbfs,
            inputGainDb = inputGainDb,
            onChangeInputGain = onChangeInputGain,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        // H: Take ± / Scene ± manual bump buttons
        com.example.recorderproject.ui.components.TakeSceneBumpRow(
            onTakeMinus = onTakeMinus1,
            onTakePlus = onBumpTake,
            onSceneMinus = onSceneMinus1,
            onSceneMinusDot1 = onSubsceneMinus,
            onSceneDot1 = onBumpSubscene,
            onScenePlus = onScenePlus1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

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
                shortTermLufs = lufsDb,
                integratedLufs = lufsDb,
                truePeakDbtp = liveTpDbtp,
                loudnessTarget = loudnessTarget,
                onSlateTone = onSlateTone,
                sceneName = sceneName,
                fileName = fileName,
                micSource = micSource,
                phaseCorrelation = phaseCorrelation,
                channelCount = channelCount,
                layout = workspaceLayout,
                isPro = isProUser,
                onUpgrade = onUpgradeFeature,
            )
        }

        Text(
            "✎ Customize layout",
            color = com.example.recorderproject.ui.theme.MeatYellow,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenCustomize)
                .padding(vertical = 12.dp),
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
            monitorOn = monitorOn,
            onToggleMonitor = onToggleMonitor,
            monitorRmsDb = monitorRmsDb,
            maxDurationMinutes = maxDurationMinutes,
            onChangeMaxDuration = onChangeMaxDuration,
        )
    }
}
