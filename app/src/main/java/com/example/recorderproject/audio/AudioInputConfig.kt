@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.example.recorderproject.audio

import android.content.Context
import android.media.MediaRecorder
import com.example.recorderproject.billing.ProFeature
import com.example.recorderproject.data.SettingsDataStore
import com.example.recorderproject.data.SettingsSnapshot
import com.example.recorderproject.model.RecordingQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Owns the audio-input configuration: sample rate, bit depth, channel count, input gain,
 * audio source (MIC/CAMCORDER/…), mic-source label, the quality preset, and external-device
 * (USB / Bluetooth / wired) routing via [UsbAudioDetector]. Extracted from RecorderViewModel
 * (issue #13 step 6).
 *
 * Pushes the input config onto the injected [recorder] (5 setters); `sampleRate` is not a
 * setter — the recording flow reads [sampleRate] and passes it to `recorder.start(...)`.
 * Android-coupled, so verified by compile + the app's existing suite + device smoke.
 */
class AudioInputConfig(
    app: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsDataStore,
    private val isHydrated: () -> Boolean,
    private val requirePro: (ProFeature) -> Boolean,
    private val recorder: AudioRecorderManager,
) {
    private val detector = UsbAudioDetector(app)
    val externalInputDevices: StateFlow<List<UsbAudioDetector.UsbDevice>> = detector.devices

    private val _sampleRate = MutableStateFlow(48000)
    val sampleRate: StateFlow<Int> = _sampleRate

    private val _bitDepth = MutableStateFlow(16)
    val bitDepth: StateFlow<Int> = _bitDepth

    private val _channelCount = MutableStateFlow(1)
    val channelCount: StateFlow<Int> = _channelCount

    private val _inputGainDb = MutableStateFlow(0f)
    val inputGainDb: StateFlow<Float> = _inputGainDb

    private val _audioSource = MutableStateFlow(1) // Default MIC
    val audioSource: StateFlow<Int> = _audioSource

    private val _audioSourceName = MutableStateFlow("Microphone")
    val audioSourceName: StateFlow<String> = _audioSourceName

    private val _micSourceLabel = MutableStateFlow("Microphone")
    val micSourceLabel: StateFlow<String> = _micSourceLabel

    private val _quality = MutableStateFlow(RecordingQuality.Default)
    val quality: StateFlow<RecordingQuality> = _quality

    // Debounced persistence for the input-gain slider — UI updates instantly, disk write
    // is coalesced to at most one per 150 ms of quiet time.
    private val gainDbPersist = MutableSharedFlow<Float>(extraBufferCapacity = 64)

    fun updateSampleRate(value: Int) {
        // Free tier caps at 48 kHz; 96 kHz is Pro.
        if (value > ProFeature.FREE_MAX_SAMPLE_RATE &&
            !requirePro(ProFeature.HIGH_RES_AUDIO)
        ) return
        _sampleRate.value = value
        if (isHydrated()) scope.launch { settings.setSampleRate(value) }
    }

    fun updateBitDepth(v: Int) {
        // Free tier caps at 16-bit; 24/32-bit float is Pro.
        if (v > ProFeature.FREE_MAX_BIT_DEPTH &&
            !requirePro(ProFeature.HIGH_RES_AUDIO)
        ) return
        _bitDepth.value = v
        recorder.setBitDepth(v)
        if (isHydrated()) scope.launch { settings.setBitDepth(v) }
    }

    fun updateChannelCount(v: Int) {
        _channelCount.value = v.coerceIn(1, 2)
        recorder.setChannelCount(_channelCount.value)
        if (isHydrated()) scope.launch { settings.setChannelCount(_channelCount.value) }
    }

    fun updateInputGainDb(db: Float) {
        val clamped = db.coerceIn(-12f, 24f)
        _inputGainDb.value = clamped
        recorder.setInputGain(dbToLinear(clamped))
        if (isHydrated()) gainDbPersist.tryEmit(clamped)
    }

    fun setQuality(q: RecordingQuality) {
        _quality.value = q
        _sampleRate.value = q.sampleRate
        _bitDepth.value = q.bitDepth
        _channelCount.value = q.channelCount
        if (isHydrated()) scope.launch {
            settings.setQualityPreset(q.name)
            settings.setSampleRate(q.sampleRate)
            settings.setBitDepth(q.bitDepth)
            settings.setChannelCount(q.channelCount)
        }
    }

    fun setMicSource(label: String) {
        _micSourceLabel.value = label
        updateAudioSource(label)
        if (isHydrated()) scope.launch { settings.setMicSourceLabel(label) }
    }

    fun updateAudioSource(name: String) {
        val source = AudioRecorderManager.AUDIO_SOURCES.find { it.first == name }
        if (source != null) {
            _audioSource.value = source.second
            _audioSourceName.value = source.first
            if (isHydrated()) scope.launch { settings.setAudioSourceName(source.first) }
        }
    }

    /** Select a specific hardware input device (USB, BT, wired headset). Pro feature. */
    fun selectInputDevice(device: UsbAudioDetector.UsbDevice) {
        // External mic routing (USB / BT / wired) is a Pro feature.
        if (!requirePro(ProFeature.EXTERNAL_MIC)) return
        val audioDeviceInfo = detector.preferredDeviceById(device.id)
        recorder.setPreferredDevice(audioDeviceInfo)
        _micSourceLabel.value = device.productName
        // For Bluetooth SCO mics, also switch to VOICE_COMMUNICATION source so Android
        // routes the SCO input path (required on most devices to actually capture from BT mic).
        if (device.category == UsbAudioDetector.DeviceCategory.BLUETOOTH) {
            _audioSource.value = MediaRecorder.AudioSource.VOICE_COMMUNICATION
            _audioSourceName.value = "Voice Communication"
        }
        if (isHydrated()) scope.launch { settings.setMicSourceLabel(device.productName) }
    }

    /** Clear hardware device preference — fall back to OS default for chosen AudioSource. */
    fun clearInputDevice() {
        recorder.setPreferredDevice(null)
    }

    /**
     * Set sample rate / bit depth / channel count together (used when applying a recorder
     * mode preset). Persists when hydrated. Does not touch the quality enum.
     */
    fun applyQualityValues(sampleRate: Int, bitDepth: Int, channelCount: Int) {
        _sampleRate.value = sampleRate
        _bitDepth.value = bitDepth
        _channelCount.value = channelCount
        if (isHydrated()) scope.launch {
            settings.setSampleRate(sampleRate)
            settings.setBitDepth(bitDepth)
            settings.setChannelCount(channelCount)
        }
    }

    /** Hydrate the input-config slice from a snapshot (in-memory only; no recorder push). */
    fun applySnapshot(s: SettingsSnapshot) {
        _audioSourceName.value = s.audioSourceName
        _micSourceLabel.value = s.micSourceLabel
        // Look up audio source ID by name (mirrors updateAudioSource()).
        AudioRecorderManager.AUDIO_SOURCES.find { it.first == s.audioSourceName }?.let {
            _audioSource.value = it.second
        }
        _inputGainDb.value = s.inputGainDb
        _sampleRate.value = s.sampleRate
        _bitDepth.value = s.bitDepth
        _channelCount.value = s.channelCount
        _quality.value = runCatching { RecordingQuality.valueOf(s.qualityPreset) }
            .getOrDefault(RecordingQuality.Default)
    }

    /** Push the input-config slice (gain + bit depth + channel count) onto the recorder. */
    fun rewireRecorder() {
        recorder.setInputGain(dbToLinear(_inputGainDb.value))
        recorder.setBitDepth(_bitDepth.value)
        recorder.setChannelCount(_channelCount.value)
    }

    /** Start the device detector + the debounced gain-persistence collector. */
    fun start() {
        detector.start()
        scope.launch {
            gainDbPersist
                .debounce(150)
                .collect { settings.setInputGainDb(it) }
        }
    }

    /** Stop the device detector (call from ViewModel.onCleared). */
    fun stop() {
        detector.stop()
    }

    private fun dbToLinear(db: Float): Float =
        kotlin.math.exp(kotlin.math.ln(10.0) * db / 20.0).toFloat()
}
