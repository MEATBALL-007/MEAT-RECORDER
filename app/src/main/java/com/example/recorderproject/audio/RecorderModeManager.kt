package com.example.recorderproject.audio

import com.example.recorderproject.data.SettingsDataStore
import com.example.recorderproject.model.RecorderMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the selected recording mode (N2) and applying its preset. Extracted from
 * RecorderViewModel (issue #13 step 11). Selecting a non-CUSTOM mode applies its
 * sample/bit/channel quality (via [audioConfig]) and noise-reduction default (via
 * [setNoiseReduction], since the NR flow stays in the ViewModel), then persists.
 *
 * The exposed [recorderMode] flow is consumed by WorkspaceManager (per-mode layouts).
 */
class RecorderModeManager(
    private val scope: CoroutineScope,
    private val settings: SettingsDataStore,
    private val isHydrated: () -> Boolean,
    private val audioConfig: AudioInputConfig,
    private val setNoiseReduction: (Boolean) -> Unit,
) {
    private val _recorderMode = MutableStateFlow(RecorderMode.Default)
    val recorderMode: StateFlow<RecorderMode> = _recorderMode

    fun select(mode: RecorderMode) {
        _recorderMode.value = mode
        // Apply preset (except for CUSTOM — the user controls those themselves). The
        // sample/bit/channel slice (incl. persistence) is delegated to AudioInputConfig.
        if (mode != RecorderMode.CUSTOM) {
            audioConfig.applyQualityValues(mode.sampleRate, mode.bitDepth, mode.channelCount)
            setNoiseReduction(mode.noiseReduction)
        }
        if (isHydrated()) scope.launch {
            settings.setRecorderMode(mode.name)
            if (mode != RecorderMode.CUSTOM) {
                settings.setNoiseReduction(mode.noiseReduction)
            }
        }
    }

    /** Hydrate the persisted mode from a snapshot (in-memory only). */
    fun applySnapshot(recorderMode: String) {
        _recorderMode.value = runCatching { RecorderMode.valueOf(recorderMode) }
            .getOrDefault(RecorderMode.CUSTOM)
    }
}
