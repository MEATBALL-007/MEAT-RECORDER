# Settings Persistence & Factory Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every recorder/EQ/playback setting in `RecorderViewModel` survive app close/reopen, and add a "Reset to factory defaults" button in `MeatRecSettings`.

**Architecture:** Two-store split. `SettingsDataStore` (Jetpack DataStore) is expanded to cover every persisted in-memory setting and is wiped on Reset Factory. Existing `SharedPreferences` (`MODE_PRIVATE` + `meatrec_ui`) keep their current roles (onboarding, theme, custom presets, sort/filter) and are **not** touched by reset. A single `Defaults` object is the single source of truth for default values, used by both hydration fallback and `resetFactory()`. Hot-path setters (`inputGainDb`, `liveEqBandGains`) get debounced writes.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidViewModel, Kotlin Coroutines (StateFlow + Flow.debounce), `androidx.datastore:datastore-preferences` (already on the classpath).

**Spec:** `docs/superpowers/specs/2026-05-27-settings-persistence-and-factory-reset-design.md`

---

## File Structure

**New files:**
- `app/src/main/java/com/example/recorderproject/data/Defaults.kt` — single source of truth for default values of every persisted setting. ~50 lines.
- `app/src/main/java/com/example/recorderproject/data/SettingsSnapshot.kt` — immutable data class capturing a full read of `SettingsDataStore`. ~45 lines.
- `app/src/test/java/com/example/recorderproject/data/DefaultsTest.kt` — pure JVM unit test for `Defaults`. ~30 lines.

**Modified files:**
- `app/src/main/java/com/example/recorderproject/data/SettingsDataStore.kt` — expand from ~12 keys to ~35 keys; add `snapshot()` and `clear()`. The file grows but its responsibility stays singular (preferences I/O). Roughly +180 lines.
- `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` — add `settings` field, `hydrated` flag, `init { hydrate }`, `applyDefaults()`, `rewireRecorderFromState()`, `resetFactory()`, and append write-through one-liners to ~30 existing setters. Two debounced flows for hot paths. Roughly +120 lines edited / added.
- `app/src/main/java/com/example/recorderproject/MainActivity.kt` — `directoryLauncher` callback must call `takePersistableUriPermission` so the SAF URI grant survives process death. ~6 lines changed.
- `app/src/main/java/com/example/recorderproject/ui/MeatRecSettings.kt` — add the RESET section at the bottom of the scrollable body + an `AlertDialog` for confirmation. ~70 lines added.

Why these splits: `Defaults` and `SettingsSnapshot` live next to `SettingsDataStore` in `data/` so the whole persistence story is in one package. Keeping defaults in their own file (not inside `SettingsDataStore`) means the ViewModel imports `Defaults` without pulling DataStore types into the VM file.

**Note on test coverage:** This codebase has no Robolectric setup. Pure-JVM unit tests are practical for the `Defaults` object only. DataStore I/O and ViewModel hydration/reset are verified by manual UI testing (laid out in the final task). Adding Robolectric is out of scope.

---

## Task 1: Create `Defaults` source of truth

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/data/Defaults.kt`
- Test:   `app/src/test/java/com/example/recorderproject/data/DefaultsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/recorderproject/data/DefaultsTest.kt`:

```kotlin
package com.example.recorderproject.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultsTest {
    @Test
    fun `recording defaults match spec`() {
        assertEquals("CUSTOM", Defaults.RECORDER_MODE)
        assertEquals("Microphone", Defaults.AUDIO_SOURCE_NAME)
        assertEquals("Microphone", Defaults.MIC_SOURCE_LABEL)
        assertEquals(0f, Defaults.INPUT_GAIN_DB)
        assertEquals(true, Defaults.NOISE_REDUCTION)
        assertEquals(48000, Defaults.SAMPLE_RATE_HZ)
        assertEquals(16, Defaults.BIT_DEPTH)
        assertEquals(1, Defaults.CHANNEL_COUNT)
        assertEquals(0, Defaults.COUNTDOWN_SEC)
        assertEquals(0, Defaults.MAX_DURATION_MIN)
        assertEquals(0, Defaults.AUTO_STOP_MIN)
        assertEquals("Scene 1", Defaults.SCENE_NAME)
    }

    @Test
    fun `live toggles default off`() {
        assertEquals(false, Defaults.LIVE_NOISE_GATE)
        assertEquals(false, Defaults.AGC)
        assertEquals(false, Defaults.HI_PASS)
        assertEquals(false, Defaults.ANTI_CLIP)
        assertEquals(false, Defaults.COMPRESSOR)
        assertEquals(false, Defaults.STEREO_WIDENER)
        assertEquals(false, Defaults.VAD)
        assertEquals(false, Defaults.LIVE_EQ_ENABLED)
        assertEquals(6, Defaults.LIVE_EQ_BAND_GAINS.size)
        Defaults.LIVE_EQ_BAND_GAINS.forEach { assertEquals(0f, it) }
    }

    @Test
    fun `playback defaults match spec`() {
        assertEquals(1f, Defaults.PLAYBACK_SPEED)
        assertEquals(false, Defaults.PLAYBACK_LOOP)
        assertEquals(1f, Defaults.PLAYBACK_VOLUME)
    }

    @Test
    fun `misc defaults match spec`() {
        assertNull(Defaults.SAVE_DIRECTORY_URI)
        assertEquals(false, Defaults.GROUP_BY_SCENE)
        assertEquals(true, Defaults.LOCKSCREEN_CONTROLS)
        assertEquals(false, Defaults.CLOUD_BACKUP)
    }

    @Test
    fun `eq defaults match spec`() {
        assertEquals("PARAMETRIC", Defaults.EQ_MODE)
        assertEquals("TWO_D", Defaults.EQ_VIEW_MODE)
        assertEquals("BOTH", Defaults.EQ_APPLY_SAVE_MODE)
        assertEquals(false, Defaults.EQ_BYPASSED)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.data.DefaultsTest"`
Expected: FAIL — `Unresolved reference: Defaults`

- [ ] **Step 3: Create `Defaults`**

Create `app/src/main/java/com/example/recorderproject/data/Defaults.kt`:

```kotlin
package com.example.recorderproject.data

import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.EQChainJson

/**
 * Single source of truth for the declared default value of every persisted
 * setting in [SettingsDataStore]. Used both as the fallback when a key is
 * absent during hydration AND as the target state of `RecorderViewModel.resetFactory()`.
 *
 * Keeping these in one place prevents drift between "what gets read on first
 * launch" and "what Reset Factory restores".
 */
object Defaults {
    // Recording
    const val RECORDER_MODE: String = "CUSTOM"
    const val AUDIO_SOURCE_NAME: String = "Microphone"
    const val MIC_SOURCE_LABEL: String = "Microphone"
    const val INPUT_GAIN_DB: Float = 0f
    const val NOISE_REDUCTION: Boolean = true
    const val SAMPLE_RATE_HZ: Int = 48000
    const val BIT_DEPTH: Int = 16
    const val CHANNEL_COUNT: Int = 1
    const val COUNTDOWN_SEC: Int = 0
    const val MAX_DURATION_MIN: Int = 0
    const val AUTO_STOP_MIN: Int = 0
    const val QUALITY_PRESET: String = "Default"
    const val SCENE_NAME: String = "Scene 1"

    // Live toggles
    const val LIVE_NOISE_GATE: Boolean = false
    const val AGC: Boolean = false
    const val HI_PASS: Boolean = false
    const val ANTI_CLIP: Boolean = false
    const val COMPRESSOR: Boolean = false
    const val STEREO_WIDENER: Boolean = false
    const val VAD: Boolean = false
    const val LIVE_EQ_ENABLED: Boolean = false
    val LIVE_EQ_BAND_GAINS: FloatArray get() = FloatArray(6) // fresh copy each access
    const val LIVE_EQ_BAND_GAINS_CSV: String = "0.0,0.0,0.0,0.0,0.0,0.0"

    // EQ editor
    val CURRENT_EQ_CHAIN_JSON: String get() = EQChainJson.toJsonString(EQChain.empty())
    const val EQ_MODE: String = "PARAMETRIC"
    const val EQ_VIEW_MODE: String = "TWO_D"
    const val EQ_APPLY_SAVE_MODE: String = "BOTH"
    const val EQ_BYPASSED: Boolean = false

    // Playback
    const val PLAYBACK_SPEED: Float = 1f
    const val PLAYBACK_LOOP: Boolean = false
    const val PLAYBACK_VOLUME: Float = 1f

    // Misc
    val SAVE_DIRECTORY_URI: String? = null
    const val GROUP_BY_SCENE: Boolean = false
    const val LOCKSCREEN_CONTROLS: Boolean = true
    const val CLOUD_BACKUP: Boolean = false
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.data.DefaultsTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/data/Defaults.kt \
        app/src/test/java/com/example/recorderproject/data/DefaultsTest.kt
git commit -m "feat(data): add Defaults source-of-truth for persisted settings"
```

---

## Task 2: Create `SettingsSnapshot` data class

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/data/SettingsSnapshot.kt`

- [ ] **Step 1: Create the snapshot data class**

Create `app/src/main/java/com/example/recorderproject/data/SettingsSnapshot.kt`:

```kotlin
package com.example.recorderproject.data

/**
 * Immutable snapshot of every persisted setting, as read from [SettingsDataStore].
 * Returned by `SettingsDataStore.snapshot()` and applied to in-memory state by
 * `RecorderViewModel.applySnapshot()` during hydration.
 *
 * Default values come from [Defaults] so a snapshot built from a missing/empty
 * DataStore is identical to the post-`resetFactory()` state.
 */
data class SettingsSnapshot(
    // Recording
    val recorderMode: String = Defaults.RECORDER_MODE,
    val audioSourceName: String = Defaults.AUDIO_SOURCE_NAME,
    val micSourceLabel: String = Defaults.MIC_SOURCE_LABEL,
    val inputGainDb: Float = Defaults.INPUT_GAIN_DB,
    val noiseReduction: Boolean = Defaults.NOISE_REDUCTION,
    val sampleRate: Int = Defaults.SAMPLE_RATE_HZ,
    val bitDepth: Int = Defaults.BIT_DEPTH,
    val channelCount: Int = Defaults.CHANNEL_COUNT,
    val countdownSec: Int = Defaults.COUNTDOWN_SEC,
    val maxDurationMin: Int = Defaults.MAX_DURATION_MIN,
    val autoStopMin: Int = Defaults.AUTO_STOP_MIN,
    val qualityPreset: String = Defaults.QUALITY_PRESET,
    val sceneName: String = Defaults.SCENE_NAME,
    // Live toggles
    val liveNoiseGate: Boolean = Defaults.LIVE_NOISE_GATE,
    val agc: Boolean = Defaults.AGC,
    val hiPass: Boolean = Defaults.HI_PASS,
    val antiClip: Boolean = Defaults.ANTI_CLIP,
    val compressor: Boolean = Defaults.COMPRESSOR,
    val stereoWidener: Boolean = Defaults.STEREO_WIDENER,
    val vad: Boolean = Defaults.VAD,
    val liveEqEnabled: Boolean = Defaults.LIVE_EQ_ENABLED,
    val liveEqBandGains: FloatArray = Defaults.LIVE_EQ_BAND_GAINS,
    // EQ editor
    val currentEqChainJson: String = Defaults.CURRENT_EQ_CHAIN_JSON,
    val eqMode: String = Defaults.EQ_MODE,
    val eqViewMode: String = Defaults.EQ_VIEW_MODE,
    val eqApplySaveMode: String = Defaults.EQ_APPLY_SAVE_MODE,
    val eqBypassed: Boolean = Defaults.EQ_BYPASSED,
    // Playback
    val playbackSpeed: Float = Defaults.PLAYBACK_SPEED,
    val playbackLoop: Boolean = Defaults.PLAYBACK_LOOP,
    val playbackVolume: Float = Defaults.PLAYBACK_VOLUME,
    // Misc
    val saveDirectoryUri: String? = Defaults.SAVE_DIRECTORY_URI,
    val groupByScene: Boolean = Defaults.GROUP_BY_SCENE,
    val lockScreenControls: Boolean = Defaults.LOCKSCREEN_CONTROLS,
    val cloudBackup: Boolean = Defaults.CLOUD_BACKUP,
)
```

- [ ] **Step 2: Build to verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/data/SettingsSnapshot.kt
git commit -m "feat(data): add SettingsSnapshot data class"
```

---

## Task 3: Expand `SettingsDataStore` to cover all persisted settings

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/data/SettingsDataStore.kt` (full rewrite)

- [ ] **Step 1: Rewrite `SettingsDataStore.kt`**

Replace the contents of `app/src/main/java/com/example/recorderproject/data/SettingsDataStore.kt` with:

```kotlin
package com.example.recorderproject.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * DataStore-backed persistence for every recorder/EQ/playback setting that the
 * user can change. Wiped wholesale by `RecorderViewModel.resetFactory()`.
 *
 * Companion SharedPreferences in `MainActivity` (`MODE_PRIVATE`) and
 * `RecorderViewModel` (`meatrec_ui`) hold app-level state (onboarding, theme,
 * custom presets, sort/filter) and are NOT touched by reset.
 */
class SettingsDataStore(private val context: Context) {

    // ----- Recording -----
    private val recorderModeKey = stringPreferencesKey("recorder_mode")
    private val audioSourceNameKey = stringPreferencesKey("audio_source_name")
    private val micSourceLabelKey = stringPreferencesKey("mic_source_label")
    private val inputGainDbKey = floatPreferencesKey("input_gain_db")
    private val noiseReductionKey = booleanPreferencesKey("noise_reduction")
    private val sampleRateKey = intPreferencesKey("sample_rate_hz")
    private val bitDepthKey = intPreferencesKey("bit_depth")
    private val channelCountKey = intPreferencesKey("channel_count")
    private val countdownSecKey = intPreferencesKey("countdown_sec")
    private val maxDurationMinKey = intPreferencesKey("max_duration_min")
    private val autoStopMinKey = intPreferencesKey("auto_stop_min")
    private val qualityPresetKey = stringPreferencesKey("quality_preset")
    private val sceneNameKey = stringPreferencesKey("scene_name")

    // ----- Live toggles -----
    private val liveNoiseGateKey = booleanPreferencesKey("live_noise_gate")
    private val agcKey = booleanPreferencesKey("agc")
    private val hiPassKey = booleanPreferencesKey("hi_pass")
    private val antiClipKey = booleanPreferencesKey("anti_clip")
    private val compressorKey = booleanPreferencesKey("compressor")
    private val stereoWidenerKey = booleanPreferencesKey("stereo_widener")
    private val vadKey = booleanPreferencesKey("vad")
    private val liveEqEnabledKey = booleanPreferencesKey("live_eq_enabled")
    private val liveEqBandGainsKey = stringPreferencesKey("live_eq_band_gains")

    // ----- EQ editor -----
    private val currentEqChainJsonKey = stringPreferencesKey("current_eq_chain_json")
    private val eqModeKey = stringPreferencesKey("eq_mode")
    private val eqViewModeKey = stringPreferencesKey("eq_view_mode")
    private val eqApplySaveModeKey = stringPreferencesKey("eq_apply_save_mode")
    private val eqBypassedKey = booleanPreferencesKey("eq_bypassed")

    // ----- Playback -----
    private val playbackSpeedKey = floatPreferencesKey("playback_speed")
    private val playbackLoopKey = booleanPreferencesKey("playback_loop")
    private val playbackVolumeKey = floatPreferencesKey("playback_volume")

    // ----- Misc -----
    private val saveDirectoryUriKey = stringPreferencesKey("save_directory_uri")
    private val groupByScene = booleanPreferencesKey("group_by_scene")
    private val lockScreenControlsKey = booleanPreferencesKey("lockscreen_controls")
    private val cloudBackupKey = booleanPreferencesKey("cloud_backup")

    /** Read every key at once and return a snapshot. */
    suspend fun snapshot(): SettingsSnapshot {
        val p = context.dataStore.data.first()
        return SettingsSnapshot(
            recorderMode = p[recorderModeKey] ?: Defaults.RECORDER_MODE,
            audioSourceName = p[audioSourceNameKey] ?: Defaults.AUDIO_SOURCE_NAME,
            micSourceLabel = p[micSourceLabelKey] ?: Defaults.MIC_SOURCE_LABEL,
            inputGainDb = p[inputGainDbKey] ?: Defaults.INPUT_GAIN_DB,
            noiseReduction = p[noiseReductionKey] ?: Defaults.NOISE_REDUCTION,
            sampleRate = p[sampleRateKey] ?: Defaults.SAMPLE_RATE_HZ,
            bitDepth = p[bitDepthKey] ?: Defaults.BIT_DEPTH,
            channelCount = p[channelCountKey] ?: Defaults.CHANNEL_COUNT,
            countdownSec = p[countdownSecKey] ?: Defaults.COUNTDOWN_SEC,
            maxDurationMin = p[maxDurationMinKey] ?: Defaults.MAX_DURATION_MIN,
            autoStopMin = p[autoStopMinKey] ?: Defaults.AUTO_STOP_MIN,
            qualityPreset = p[qualityPresetKey] ?: Defaults.QUALITY_PRESET,
            sceneName = p[sceneNameKey] ?: Defaults.SCENE_NAME,
            liveNoiseGate = p[liveNoiseGateKey] ?: Defaults.LIVE_NOISE_GATE,
            agc = p[agcKey] ?: Defaults.AGC,
            hiPass = p[hiPassKey] ?: Defaults.HI_PASS,
            antiClip = p[antiClipKey] ?: Defaults.ANTI_CLIP,
            compressor = p[compressorKey] ?: Defaults.COMPRESSOR,
            stereoWidener = p[stereoWidenerKey] ?: Defaults.STEREO_WIDENER,
            vad = p[vadKey] ?: Defaults.VAD,
            liveEqEnabled = p[liveEqEnabledKey] ?: Defaults.LIVE_EQ_ENABLED,
            liveEqBandGains = parseCsvFloats(p[liveEqBandGainsKey], expected = 6),
            currentEqChainJson = p[currentEqChainJsonKey] ?: Defaults.CURRENT_EQ_CHAIN_JSON,
            eqMode = p[eqModeKey] ?: Defaults.EQ_MODE,
            eqViewMode = p[eqViewModeKey] ?: Defaults.EQ_VIEW_MODE,
            eqApplySaveMode = p[eqApplySaveModeKey] ?: Defaults.EQ_APPLY_SAVE_MODE,
            eqBypassed = p[eqBypassedKey] ?: Defaults.EQ_BYPASSED,
            playbackSpeed = p[playbackSpeedKey] ?: Defaults.PLAYBACK_SPEED,
            playbackLoop = p[playbackLoopKey] ?: Defaults.PLAYBACK_LOOP,
            playbackVolume = p[playbackVolumeKey] ?: Defaults.PLAYBACK_VOLUME,
            saveDirectoryUri = p[saveDirectoryUriKey], // null when absent
            groupByScene = p[groupByScene] ?: Defaults.GROUP_BY_SCENE,
            lockScreenControls = p[lockScreenControlsKey] ?: Defaults.LOCKSCREEN_CONTROLS,
            cloudBackup = p[cloudBackupKey] ?: Defaults.CLOUD_BACKUP,
        )
    }

    /** Wipe every key — used by Reset Factory. */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    // ----- Setters -----
    suspend fun setRecorderMode(v: String) = context.dataStore.edit { it[recorderModeKey] = v }
    suspend fun setAudioSourceName(v: String) = context.dataStore.edit { it[audioSourceNameKey] = v }
    suspend fun setMicSourceLabel(v: String) = context.dataStore.edit { it[micSourceLabelKey] = v }
    suspend fun setInputGainDb(v: Float) = context.dataStore.edit { it[inputGainDbKey] = v }
    suspend fun setNoiseReduction(v: Boolean) = context.dataStore.edit { it[noiseReductionKey] = v }
    suspend fun setSampleRate(v: Int) = context.dataStore.edit { it[sampleRateKey] = v }
    suspend fun setBitDepth(v: Int) = context.dataStore.edit { it[bitDepthKey] = v }
    suspend fun setChannelCount(v: Int) = context.dataStore.edit { it[channelCountKey] = v }
    suspend fun setCountdownSec(v: Int) = context.dataStore.edit { it[countdownSecKey] = v }
    suspend fun setMaxDurationMin(v: Int) = context.dataStore.edit { it[maxDurationMinKey] = v }
    suspend fun setAutoStopMin(v: Int) = context.dataStore.edit { it[autoStopMinKey] = v }
    suspend fun setQualityPreset(v: String) = context.dataStore.edit { it[qualityPresetKey] = v }
    suspend fun setSceneName(v: String) = context.dataStore.edit { it[sceneNameKey] = v }

    suspend fun setLiveNoiseGate(v: Boolean) = context.dataStore.edit { it[liveNoiseGateKey] = v }
    suspend fun setAgc(v: Boolean) = context.dataStore.edit { it[agcKey] = v }
    suspend fun setHiPass(v: Boolean) = context.dataStore.edit { it[hiPassKey] = v }
    suspend fun setAntiClip(v: Boolean) = context.dataStore.edit { it[antiClipKey] = v }
    suspend fun setCompressor(v: Boolean) = context.dataStore.edit { it[compressorKey] = v }
    suspend fun setStereoWidener(v: Boolean) = context.dataStore.edit { it[stereoWidenerKey] = v }
    suspend fun setVad(v: Boolean) = context.dataStore.edit { it[vadKey] = v }
    suspend fun setLiveEqEnabled(v: Boolean) = context.dataStore.edit { it[liveEqEnabledKey] = v }
    suspend fun setLiveEqBandGains(v: FloatArray) = context.dataStore.edit {
        it[liveEqBandGainsKey] = v.joinToString(",")
    }

    suspend fun setCurrentEqChainJson(v: String) = context.dataStore.edit { it[currentEqChainJsonKey] = v }
    suspend fun setEqMode(v: String) = context.dataStore.edit { it[eqModeKey] = v }
    suspend fun setEqViewMode(v: String) = context.dataStore.edit { it[eqViewModeKey] = v }
    suspend fun setEqApplySaveMode(v: String) = context.dataStore.edit { it[eqApplySaveModeKey] = v }
    suspend fun setEqBypassed(v: Boolean) = context.dataStore.edit { it[eqBypassedKey] = v }

    suspend fun setPlaybackSpeed(v: Float) = context.dataStore.edit { it[playbackSpeedKey] = v }
    suspend fun setPlaybackLoop(v: Boolean) = context.dataStore.edit { it[playbackLoopKey] = v }
    suspend fun setPlaybackVolume(v: Float) = context.dataStore.edit { it[playbackVolumeKey] = v }

    suspend fun setSaveDirectoryUri(v: String?) = context.dataStore.edit {
        if (v == null) it.remove(saveDirectoryUriKey) else it[saveDirectoryUriKey] = v
    }
    suspend fun setGroupByScene(v: Boolean) = context.dataStore.edit { it[groupByScene] = v }
    suspend fun setLockScreenControls(v: Boolean) = context.dataStore.edit { it[lockScreenControlsKey] = v }
    suspend fun setCloudBackup(v: Boolean) = context.dataStore.edit { it[cloudBackupKey] = v }

    private fun parseCsvFloats(csv: String?, expected: Int): FloatArray {
        if (csv.isNullOrBlank()) return FloatArray(expected)
        val parts = csv.split(",")
        if (parts.size != expected) return FloatArray(expected)
        return FloatArray(expected) { i -> parts[i].toFloatOrNull() ?: 0f }
    }
}
```

- [ ] **Step 2: Build to verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If anything breaks downstream, it's likely because the old class exposed `theme`, `eqViewMode`, `eqApplySaveMode` flows that callers consumed. Check with:

```bash
grep -rn "SettingsDataStore" app/src/main/
```

If the only call site is `RecorderViewModel` (it should be — this class is currently dormant), no fixes needed. If there are callers, leave the missing methods broken — Task 5 wires them properly.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/data/SettingsDataStore.kt
git commit -m "feat(data): expand SettingsDataStore for full setting coverage"
```

---

## Task 4: Wire `settings` field and `applyDefaults`/`applySnapshot` into ViewModel

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

- [ ] **Step 1: Add imports + settings field + hydrated flag**

In `RecorderViewModel.kt`, after the existing imports block (around line 32), add:

```kotlin
import com.example.recorderproject.data.Defaults
import com.example.recorderproject.data.SettingsDataStore
import com.example.recorderproject.data.SettingsSnapshot
import com.example.recorderproject.model.RecorderMode
import com.example.recorderproject.model.RecordingQuality
import com.example.recorderproject.model.EQBand
import com.example.recorderproject.model.EQBandType
import com.example.recorderproject.model.EQChainJson
import com.example.recorderproject.model.EQEditMode
```

(Don't worry about duplicate imports — the IDE/compiler will flag and you can remove the duplicate from below. The important thing is `Defaults`, `SettingsDataStore`, `SettingsSnapshot` are imported.)

After the line `private val mediaPlayer = MediaPlayer()` (around line 39), add:

```kotlin
private val settings = SettingsDataStore(application)
private val hydrated = MutableStateFlow(false)
```

- [ ] **Step 2: Add `applyDefaults()` helper**

Inside the `RecorderViewModel` class (place near the bottom, just above `override fun onCleared()`), add:

```kotlin
/**
 * Reassign every in-memory persisted-setting StateFlow to its declared default.
 * Used by both `applySnapshot(SettingsSnapshot())` and `resetFactory()`.
 *
 * NOTE: This sets the StateFlows only — it does NOT push state into
 * `AudioRecorderManager`. Call `rewireRecorderFromState()` after this if you
 * need the native recorder to pick up the new state.
 */
private fun applyDefaults() = applySnapshot(SettingsSnapshot())

/** Apply a SettingsSnapshot to the in-memory StateFlows. */
private fun applySnapshot(s: SettingsSnapshot) {
    _recorderMode.value = runCatching { RecorderMode.valueOf(s.recorderMode) }
        .getOrDefault(RecorderMode.CUSTOM)
    _audioSourceName.value = s.audioSourceName
    _micSourceLabel.value = s.micSourceLabel
    // Look up audio source ID by name (mirrors updateAudioSource())
    AudioRecorderManager.AUDIO_SOURCES.find { it.first == s.audioSourceName }?.let {
        _audioSource.value = it.second
    }
    _inputGainDb.value = s.inputGainDb
    _noiseReductionEnabled.value = s.noiseReduction
    _sampleRate.value = s.sampleRate
    _bitDepth.value = s.bitDepth
    _channelCount.value = s.channelCount
    _countdownSeconds.value = s.countdownSec
    _maxDurationMinutes.value = s.maxDurationMin
    _autoStopMinutes.value = s.autoStopMin
    _quality.value = runCatching { RecordingQuality.valueOf(s.qualityPreset) }
        .getOrDefault(RecordingQuality.Default)
    _sceneName.value = s.sceneName

    _liveNoiseGateOn.value = s.liveNoiseGate
    _agcOn.value = s.agc
    _hiPassOn.value = s.hiPass
    _antiClipOn.value = s.antiClip
    _compressorOn.value = s.compressor
    _stereoWidenerOn.value = s.stereoWidener
    _vadOn.value = s.vad
    _liveEqEnabled.value = s.liveEqEnabled
    _liveEqBandGains.value = s.liveEqBandGains.copyOf()

    _currentEQChain.value = runCatching {
        EQChainJson.fromJsonString(s.currentEqChainJson)
    }.getOrNull() ?: com.example.recorderproject.model.EQChain.empty()
    _eqMode.value = runCatching { EQEditMode.valueOf(s.eqMode) }.getOrDefault(EQEditMode.PARAMETRIC)
    _eqViewMode.value = runCatching { EQViewMode.valueOf(s.eqViewMode) }.getOrDefault(EQViewMode.TWO_D)
    _eqApplySaveMode.value = runCatching { ApplySaveMode.valueOf(s.eqApplySaveMode) }
        .getOrDefault(ApplySaveMode.BOTH)
    _currentEQChain.value = _currentEQChain.value.copy(bypassed = s.eqBypassed)

    _playbackSpeed.value = s.playbackSpeed
    _playbackLoop.value = s.playbackLoop
    _playbackVolume.value = s.playbackVolume

    _saveDirectoryUri.value = s.saveDirectoryUri?.let { Uri.parse(it) }
    _groupByScene.value = s.groupByScene
    _lockScreenControlsOn.value = s.lockScreenControls
    _cloudBackupOn.value = s.cloudBackup
}
```

> Note: `RecordingQuality.valueOf` may not exist directly — `RecordingQuality` is a sealed/enum class in `model/`. If `valueOf` doesn't compile, replace the `runCatching` block with a `when` switch over the enum's known values (look at `model/RecordingQuality.kt` to see them). Keep `Default` as the fallback.

- [ ] **Step 3: Build to verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If `RecordingQuality.valueOf` fails, adapt as noted above.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "feat(vm): add settings store, applyDefaults, applySnapshot helpers"
```

---

## Task 5: Hydrate from DataStore on init + restore SAF URI grant

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

- [ ] **Step 1: Add `init { hydrate }` block**

In `RecorderViewModel`, immediately after the line `private val hydrated = MutableStateFlow(false)` you added in Task 4, add an `init` block. (If an `init` block already exists, append the hydration code at the end of it.)

```kotlin
init {
    viewModelScope.launch {
        try {
            val snapshot = settings.snapshot()
            applySnapshot(snapshot)
            // Re-claim SAF URI grant if we have one persisted
            snapshot.saveDirectoryUri?.let { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    app.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (e: SecurityException) {
                    Log.w(TAG, "Persisted SAF URI no longer granted, clearing: ${e.message}")
                    _saveDirectoryUri.value = null
                    settings.setSaveDirectoryUri(null)
                }
            }
            rewireRecorderFromState()
        } catch (e: Exception) {
            Log.e(TAG, "Settings hydration failed: ${e.message}", e)
        } finally {
            hydrated.value = true
        }
    }
}
```

- [ ] **Step 2: Add `rewireRecorderFromState()` helper**

In `RecorderViewModel`, alongside `applyDefaults()` (added in Task 4), add:

```kotlin
/**
 * Push current in-memory recorder-related state into `AudioRecorderManager`.
 * Called after hydration (to restore native state on next record) and after
 * `resetFactory()` (to push default values into the native recorder).
 *
 * The monitor toggle is intentionally NOT auto-resumed here — `monitor_enabled`
 * is not persisted (see spec § Things NOT persisted).
 */
private fun rewireRecorderFromState() {
    val gainLinear = kotlin.math.exp(
        kotlin.math.ln(10.0) * _inputGainDb.value / 20.0,
    ).toFloat()
    recorder.setInputGain(gainLinear)
    recorder.setLiveNoiseGate(_liveNoiseGateOn.value, thresholdDb = -46f)
    recorder.setAgc(_agcOn.value)
    recorder.setHiPass(_hiPassOn.value)
    recorder.setAntiClip(_antiClipOn.value)
    // Live EQ chain is pushed on demand in startRecording(); no need here.
}
```

- [ ] **Step 3: Build to verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "feat(vm): hydrate settings on init and restore SAF URI permission"
```

---

## Task 6: Append write-through to every persisted setter

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

This task touches every setter that mutates a persisted StateFlow. Each gets one extra line: `if (hydrated.value) viewModelScope.launch { settings.setX(value) }`. List below is exhaustive — work through it top to bottom and verify after.

- [ ] **Step 1: `setSortOrder` and `setFileFilter` — SKIP (these live in `meatrec_ui` SharedPrefs, intentionally preserved across reset).**

- [ ] **Step 2: `toggleLiveNoiseGate` — add write-through**

Find:
```kotlin
fun toggleLiveNoiseGate() {
    _liveNoiseGateOn.value = !_liveNoiseGateOn.value
    recorder.setLiveNoiseGate(_liveNoiseGateOn.value, thresholdDb = -46f)
}
```

Replace with:
```kotlin
fun toggleLiveNoiseGate() {
    _liveNoiseGateOn.value = !_liveNoiseGateOn.value
    recorder.setLiveNoiseGate(_liveNoiseGateOn.value, thresholdDb = -46f)
    if (hydrated.value) viewModelScope.launch { settings.setLiveNoiseGate(_liveNoiseGateOn.value) }
}
```

- [ ] **Step 3: `toggleAgc`, `toggleHiPass`, `toggleAntiClip` — same pattern**

For each, append `if (hydrated.value) viewModelScope.launch { settings.setX(_xOn.value) }` after the `recorder.setX(...)` call.

```kotlin
fun toggleAgc() {
    _agcOn.value = !_agcOn.value
    recorder.setAgc(_agcOn.value)
    if (hydrated.value) viewModelScope.launch { settings.setAgc(_agcOn.value) }
}

fun toggleHiPass() {
    _hiPassOn.value = !_hiPassOn.value
    recorder.setHiPass(_hiPassOn.value)
    if (hydrated.value) viewModelScope.launch { settings.setHiPass(_hiPassOn.value) }
}

fun toggleAntiClip() {
    _antiClipOn.value = !_antiClipOn.value
    recorder.setAntiClip(_antiClipOn.value)
    if (hydrated.value) viewModelScope.launch { settings.setAntiClip(_antiClipOn.value) }
}
```

- [ ] **Step 4: `setQuality` — write four keys (the four sub-flows it mutates)**

```kotlin
fun setQuality(q: com.example.recorderproject.model.RecordingQuality) {
    _quality.value = q
    _sampleRate.value = q.sampleRate
    _bitDepth.value = q.bitDepth
    _channelCount.value = q.channelCount
    if (hydrated.value) viewModelScope.launch {
        settings.setQualityPreset(q.name)
        settings.setSampleRate(q.sampleRate)
        settings.setBitDepth(q.bitDepth)
        settings.setChannelCount(q.channelCount)
    }
}
```

- [ ] **Step 5: `toggleVad`**

```kotlin
fun toggleVad() {
    _vadOn.value = !_vadOn.value
    if (hydrated.value) viewModelScope.launch { settings.setVad(_vadOn.value) }
}
```

- [ ] **Step 6: `selectRecorderMode`**

```kotlin
fun selectRecorderMode(mode: com.example.recorderproject.model.RecorderMode) {
    _recorderMode.value = mode
    if (mode != com.example.recorderproject.model.RecorderMode.CUSTOM) {
        _sampleRate.value = mode.sampleRate
        _bitDepth.value = mode.bitDepth
        _channelCount.value = mode.channelCount
        _noiseReductionEnabled.value = mode.noiseReduction
    }
    if (hydrated.value) viewModelScope.launch {
        settings.setRecorderMode(mode.name)
        if (mode != com.example.recorderproject.model.RecorderMode.CUSTOM) {
            settings.setSampleRate(_sampleRate.value)
            settings.setBitDepth(_bitDepth.value)
            settings.setChannelCount(_channelCount.value)
            settings.setNoiseReduction(_noiseReductionEnabled.value)
        }
    }
}
```

- [ ] **Step 7: `toggleCompressor`, `toggleStereoWidener`, `toggleCloudBackup`, `toggleGroupByScene`, `toggleLockScreenControls`**

```kotlin
fun toggleCompressor() {
    _compressorOn.value = !_compressorOn.value
    if (hydrated.value) viewModelScope.launch { settings.setCompressor(_compressorOn.value) }
}

fun toggleStereoWidener() {
    _stereoWidenerOn.value = !_stereoWidenerOn.value
    if (hydrated.value) viewModelScope.launch { settings.setStereoWidener(_stereoWidenerOn.value) }
}

fun toggleCloudBackup() {
    _cloudBackupOn.value = !_cloudBackupOn.value
    if (hydrated.value) viewModelScope.launch { settings.setCloudBackup(_cloudBackupOn.value) }
}

fun toggleGroupByScene() {
    _groupByScene.value = !_groupByScene.value
    if (hydrated.value) viewModelScope.launch { settings.setGroupByScene(_groupByScene.value) }
}

fun toggleLockScreenControls() {
    _lockScreenControlsOn.value = !_lockScreenControlsOn.value
    if (hydrated.value) viewModelScope.launch { settings.setLockScreenControls(_lockScreenControlsOn.value) }
}
```

- [ ] **Step 8: `setAutoStopMinutes`**

```kotlin
fun setAutoStopMinutes(m: Int) {
    _autoStopMinutes.value = m.coerceAtLeast(0)
    if (hydrated.value) viewModelScope.launch { settings.setAutoStopMin(_autoStopMinutes.value) }
}
```

- [ ] **Step 9: `updateBitDepth`, `updateChannelCount`, `updateCountdownSeconds`, `updateMaxDurationMinutes`, `updateSampleRate`**

```kotlin
fun updateBitDepth(v: Int) {
    _bitDepth.value = v
    if (hydrated.value) viewModelScope.launch { settings.setBitDepth(v) }
}

fun updateChannelCount(v: Int) {
    _channelCount.value = v.coerceIn(1, 2)
    if (hydrated.value) viewModelScope.launch { settings.setChannelCount(_channelCount.value) }
}

fun updateCountdownSeconds(v: Int) {
    _countdownSeconds.value = v.coerceAtLeast(0)
    if (hydrated.value) viewModelScope.launch { settings.setCountdownSec(_countdownSeconds.value) }
}

fun updateMaxDurationMinutes(v: Int) {
    _maxDurationMinutes.value = v.coerceAtLeast(0)
    if (hydrated.value) viewModelScope.launch { settings.setMaxDurationMin(_maxDurationMinutes.value) }
}

fun updateSampleRate(value: Int) {
    _sampleRate.value = value
    if (hydrated.value) viewModelScope.launch { settings.setSampleRate(value) }
}
```

- [ ] **Step 10: `updateAudioSource`, `setMicSource`, `updateSceneName`, `toggleNoiseReduction`**

```kotlin
fun updateAudioSource(name: String) {
    val source = AudioRecorderManager.AUDIO_SOURCES.find { it.first == name }
    if (source != null) {
        _audioSource.value = source.second
        _audioSourceName.value = source.first
        if (hydrated.value) viewModelScope.launch { settings.setAudioSourceName(source.first) }
    }
}

fun setMicSource(label: String) {
    _micSourceLabel.value = label
    updateAudioSource(label)
    if (hydrated.value) viewModelScope.launch { settings.setMicSourceLabel(label) }
}

fun updateSceneName(value: String) {
    _sceneName.value = value
    if (hydrated.value) viewModelScope.launch { settings.setSceneName(value) }
}

fun toggleNoiseReduction(enabled: Boolean) {
    _noiseReductionEnabled.value = enabled
    if (hydrated.value) viewModelScope.launch { settings.setNoiseReduction(enabled) }
}
```

- [ ] **Step 11: `toggleLiveEq`**

```kotlin
fun toggleLiveEq() {
    _liveEqEnabled.value = !_liveEqEnabled.value
    if (_liveEqEnabled.value) {
        recorder.setLiveEqChain(_currentEQChain.value, _sampleRate.value.toFloat())
    } else {
        recorder.setLiveEqChain(null, _sampleRate.value.toFloat())
    }
    Toast.makeText(app,
        if (_liveEqEnabled.value) "Live EQ on — applied in real time to recording"
        else "Live EQ off",
        Toast.LENGTH_SHORT).show()
    if (hydrated.value) viewModelScope.launch { settings.setLiveEqEnabled(_liveEqEnabled.value) }
}
```

- [ ] **Step 12: EQ editor setters — `onEQModeToggle`, `onEQViewModeToggle`, `onEQSaveModeChange`, `onEQToggleBypass`**

```kotlin
fun onEQModeToggle(mode: EQEditMode) {
    _eqMode.value = mode
    if (hydrated.value) viewModelScope.launch { settings.setEqMode(mode.name) }
}

fun onEQViewModeToggle(mode: EQViewMode) {
    _eqViewMode.value = mode
    if (hydrated.value) viewModelScope.launch { settings.setEqViewMode(mode.name) }
}

fun onEQSaveModeChange(mode: ApplySaveMode) {
    _eqApplySaveMode.value = mode
    if (hydrated.value) viewModelScope.launch { settings.setEqApplySaveMode(mode.name) }
}

fun onEQToggleBypass() {
    _currentEQChain.value = _currentEQChain.value.copy(bypassed = !_currentEQChain.value.bypassed)
    if (hydrated.value) viewModelScope.launch {
        settings.setEqBypassed(_currentEQChain.value.bypassed)
    }
}
```

- [ ] **Step 13: EQ chain mutations — `onEQBandChanged`, `onEQResetAll`, `onEQPresetSelected`, `onEQHumDetect`, `onEQRandomPreset`, `onEQAcceptSuggestion`, `onEQTapNotch`, `onEQDrawCurve`, `onEQUndo`, `onEQRedo`, `onEQABToggle`**

Every place `_currentEQChain.value = X` is assigned, append the persistence write. Add a helper to keep this DRY:

Add near `applyDefaults()`:

```kotlin
private fun persistCurrentEqChain() {
    if (!hydrated.value) return
    viewModelScope.launch {
        try {
            settings.setCurrentEqChainJson(
                EQChainJson.toJsonString(_currentEQChain.value)
            )
        } catch (e: Exception) {
            Log.w(TAG, "EQ chain persist failed: ${e.message}")
        }
    }
}
```

Then in **every** function that assigns `_currentEQChain.value =`, add `persistCurrentEqChain()` immediately after. Specifically:

```kotlin
fun onEQBandChanged(updated: EQBand) {
    pushEqHistory(_currentEQChain.value)
    _currentEQChain.value = _currentEQChain.value.withBand(updated)
    persistCurrentEqChain()
    if (_liveEqEnabled.value) {
        recorder.setLiveEqChain(_currentEQChain.value, _sampleRate.value.toFloat())
    }
}

fun onEQUndo() {
    val prev = eqHistory.removeLastOrNull() ?: return
    eqRedo.addLast(_currentEQChain.value)
    _currentEQChain.value = prev
    persistCurrentEqChain()
}

fun onEQRedo() {
    val next = eqRedo.removeLastOrNull() ?: return
    eqHistory.addLast(_currentEQChain.value)
    _currentEQChain.value = next
    persistCurrentEqChain()
}

fun onEQABToggle() {
    val snap = _eqSnapshot.value
    if (snap == null) {
        _eqSnapshot.value = _currentEQChain.value
    } else {
        val current = _currentEQChain.value
        _currentEQChain.value = snap
        _eqSnapshot.value = current
        persistCurrentEqChain()
    }
}

fun onEQResetAll() {
    pushEqHistory(_currentEQChain.value)
    _currentEQChain.value = com.example.recorderproject.model.EQChain.empty()
    persistCurrentEqChain()
}

fun onEQPresetSelected(preset: com.example.recorderproject.model.EQPreset) {
    pushEqHistory(_currentEQChain.value)
    _currentEQChain.value = com.example.recorderproject.model.EQChain(bands = preset.bands)
    persistCurrentEqChain()
    if (_liveEqEnabled.value) {
        recorder.setLiveEqChain(_currentEQChain.value, _sampleRate.value.toFloat())
    }
}

fun onEQHumDetect(mainsHz: Float = 60f) {
    pushEqHistory(_currentEQChain.value)
    val combNotches = com.example.recorderproject.audio.EQHumDetect.combNotches(mainsHz)
    var chain = com.example.recorderproject.model.EQChain.empty()
    for ((idx, notch) in combNotches.withIndex()) {
        if (idx >= 8) break
        chain = chain.withBand(notch.copy(id = idx + 1))
    }
    _currentEQChain.value = chain
    persistCurrentEqChain()
    Toast.makeText(app, "Placed ${combNotches.size}-notch hum comb at ${mainsHz.toInt()} Hz", Toast.LENGTH_SHORT).show()
}

fun onEQRandomPreset() {
    pushEqHistory(_currentEQChain.value)
    _currentEQChain.value = com.example.recorderproject.model.EQRandomPreset.generate()
    persistCurrentEqChain()
}

fun onEQAcceptSuggestion(band: EQBand) {
    val current = _currentEQChain.value
    val added = current.withAddedBand(band) ?: run {
        Toast.makeText(app, "8-band limit reached — disable a band first", Toast.LENGTH_SHORT).show()
        return
    }
    pushEqHistory(current)
    _currentEQChain.value = added.copy(
        noiseCutSuggestions = current.noiseCutSuggestions.filter { it.id != band.id }
    )
    persistCurrentEqChain()
}

fun onEQTapNotch(frequencyHz: Float) {
    val newBand = EQBand(
        id = 0,
        type = EQBandType.NOTCH,
        frequencyHz = frequencyHz,
        gainDb = 0f,
        q = 8f,
        enabled = true,
    )
    val added = _currentEQChain.value.withAddedBand(newBand) ?: run {
        Toast.makeText(app, "8-band limit reached — disable a band first", Toast.LENGTH_SHORT).show()
        return
    }
    pushEqHistory(_currentEQChain.value)
    _currentEQChain.value = added
    persistCurrentEqChain()
}

fun onEQDrawCurve(targetDbCurve: FloatArray) {
    val bands = com.example.recorderproject.audio.EQCurveFitter
        .fitToCurve(targetDbCurve, 20f, 20_000f, maxBands = 6)
    if (bands.isEmpty()) return
    pushEqHistory(_currentEQChain.value)
    val padded = bands + (bands.size + 1..8).map { com.example.recorderproject.model.EQBand.defaultForSlot(it) }
    _currentEQChain.value = com.example.recorderproject.model.EQChain(bands = padded.take(8))
    persistCurrentEqChain()
}
```

Leave `onEQOpen` and `onEQClose` alone — those load/save the per-file sidecar JSON, which is a different concern from the global "current EQ chain" persistence.

- [ ] **Step 14: Playback setters — `setPlaybackSpeed`, `toggleLoop`, `setPlaybackVolume`**

```kotlin
fun setPlaybackSpeed(speed: Float) {
    _playbackSpeed.value = speed
    try {
        val params = mediaPlayer.playbackParams
        params.speed = speed
        mediaPlayer.playbackParams = params
    } catch (_: Exception) {}
    if (hydrated.value) viewModelScope.launch { settings.setPlaybackSpeed(speed) }
}

fun toggleLoop() {
    _playbackLoop.value = !_playbackLoop.value
    mediaPlayer.isLooping = _playbackLoop.value
    if (hydrated.value) viewModelScope.launch { settings.setPlaybackLoop(_playbackLoop.value) }
}

fun setPlaybackVolume(v: Float) {
    val vv = v.coerceIn(0f, 1f)
    _playbackVolume.value = vv
    mediaPlayer.setVolume(vv, vv)
    if (hydrated.value) viewModelScope.launch { settings.setPlaybackVolume(vv) }
}
```

- [ ] **Step 15: `setSaveDirectoryUri`**

```kotlin
fun setSaveDirectoryUri(uri: Uri) {
    _saveDirectoryUri.value = uri
    // Take persistable grant so it survives process death
    try {
        app.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    } catch (e: SecurityException) {
        Log.w(TAG, "Could not take persistable SAF permission: ${e.message}")
    }
    if (hydrated.value) viewModelScope.launch { settings.setSaveDirectoryUri(uri.toString()) }
}
```

- [ ] **Step 16: Build to verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If any setter was missed, the build still passes — the missed setter just won't persist. Re-grep:

```bash
grep -n "_recorderMode.value\|_inputGainDb.value\|_sampleRate.value\|_bitDepth.value\|_channelCount.value\|_countdownSeconds.value\|_maxDurationMinutes.value\|_autoStopMinutes.value\|_noiseReductionEnabled.value\|_liveNoiseGateOn.value\|_agcOn.value\|_hiPassOn.value\|_antiClipOn.value\|_compressorOn.value\|_stereoWidenerOn.value\|_vadOn.value\|_liveEqEnabled.value\|_currentEQChain.value\|_eqMode.value\|_eqViewMode.value\|_eqApplySaveMode.value\|_playbackSpeed.value\|_playbackLoop.value\|_playbackVolume.value\|_saveDirectoryUri.value\|_groupByScene.value\|_lockScreenControlsOn.value\|_cloudBackupOn.value\|_sceneName.value\|_audioSourceName.value\|_micSourceLabel.value" app/src/main/java/com/example/recorderproject/RecorderViewModel.kt | grep -v "applySnapshot\|applyDefaults"
```

Each line returned should be either (a) inside `applySnapshot`/`applyDefaults` (fine), (b) inside a setter that calls `settings.setX` (fine), or (c) inside an internal use that should NOT persist (e.g., `_audioSourceName.value` set inside `updateAudioSource` — covered by Step 10).

- [ ] **Step 17: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "feat(vm): write-through every persisted setter to SettingsDataStore"
```

---

## Task 7: Debounce hot-path setters (`inputGainDb`, `liveEqBandGains`)

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

These two setters can fire dozens of times per second (slider drag, +/- band buttons). Persisting each write would thrash the preferences file.

- [ ] **Step 1: Add debounced flows**

In `RecorderViewModel`, near the top of the class (just after `private val hydrated = ...`), add:

```kotlin
@OptIn(kotlinx.coroutines.FlowPreview::class)
private val gainDbPersist = MutableSharedFlow<Float>(extraBufferCapacity = 64)

@OptIn(kotlinx.coroutines.FlowPreview::class)
private val eqBandGainsPersist = MutableSharedFlow<FloatArray>(extraBufferCapacity = 64)
```

(Add the import `import kotlinx.coroutines.flow.MutableSharedFlow` if not already present.)

In the existing `init { ... }` block (added in Task 5), append the collectors after the `hydrated.value = true` line:

```kotlin
// Debounced persistence for slider/+ - hot paths
viewModelScope.launch {
    gainDbPersist
        .debounce(150)
        .collect { settings.setInputGainDb(it) }
}
viewModelScope.launch {
    eqBandGainsPersist
        .debounce(150)
        .collect { settings.setLiveEqBandGains(it) }
}
```

(Add the import `import kotlinx.coroutines.flow.debounce` if not already present.)

- [ ] **Step 2: Modify `updateInputGainDb` to publish through the debounced flow**

```kotlin
fun updateInputGainDb(db: Float) {
    val clamped = db.coerceIn(-12f, 24f)
    _inputGainDb.value = clamped
    val linear = kotlin.math.exp(kotlin.math.ln(10.0) * clamped / 20.0).toFloat()
    recorder.setInputGain(linear)
    if (hydrated.value) gainDbPersist.tryEmit(clamped)
}
```

- [ ] **Step 3: Modify `setLiveEqBand` to publish through the debounced flow**

Inside the existing `setLiveEqBand` function, after the line `_liveEqBandGains.value = arr`, add:

```kotlin
if (hydrated.value) eqBandGainsPersist.tryEmit(arr.copyOf())
```

(The `.copyOf()` matters — the StateFlow holds the same array reference, so without it the SharedFlow would emit the same mutable instance that subsequent edits would also overwrite before the debounce fires.)

- [ ] **Step 4: Build to verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "feat(vm): debounce persistence for inputGainDb and liveEqBandGains"
```

---

## Task 8: Implement `resetFactory()`

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

- [ ] **Step 1: Add the method**

In `RecorderViewModel`, alongside `applyDefaults()`, add:

```kotlin
/**
 * Reset every persisted setting to its declared default. Does NOT touch:
 *  - SharedPreferences (onboarding_done, mode_chosen, app_theme, custom_presets,
 *    active_preset, sort_order, file_filter)
 *  - Recorded WAV files or their sidecars
 *  - CustomPresetStore (user-named EQ presets)
 *  - The in-memory _recordFiles list
 */
fun resetFactory() {
    viewModelScope.launch {
        try {
            settings.clear()
            // Tear down monitor if running — opening the mic is a session-local
            // side effect that should not persist past reset.
            if (_monitorEnabled.value) {
                try {
                    audioMonitor.stop()
                    monitorDecayJob?.cancel()
                    monitorDecayJob = null
                    audioMonitor.setLevelListener(null)
                    monitorClipUntilMs = 0L
                    _monitorLevel.value = MonitorLevel.Silent
                    val am = app.getSystemService(android.content.Context.AUDIO_SERVICE)
                        as android.media.AudioManager
                    @Suppress("DEPRECATION") am.isBluetoothScoOn = false
                    @Suppress("DEPRECATION") am.stopBluetoothSco()
                } catch (_: Exception) {}
                _monitorEnabled.value = false
            }
            applyDefaults()
            rewireRecorderFromState()
            // Clear EQ undo/redo so post-reset history doesn't reference old chains
            eqHistory.clear()
            eqRedo.clear()
            withContext(Dispatchers.Main) {
                Toast.makeText(app, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "resetFactory failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(app, "Reset failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "feat(vm): add resetFactory() to restore declared defaults"
```

---

## Task 9: Add RESET section + confirm dialog to `MeatRecSettings`

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/MeatRecSettings.kt`

- [ ] **Step 1: Add imports**

At the top of `MeatRecSettings.kt`, add (alongside existing imports):

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
```

- [ ] **Step 2: Add a state holder for the dialog**

Near the top of the `MeatRecSettings` composable body (just under `val scroll = rememberScrollState()`), add:

```kotlin
var showResetDialog by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Add the RESET section at the bottom of the scrollable Column**

In the scrollable `Column` body, immediately after the `AUDIO QUALITY` block (after the `SampleRatePillRow(...)` call closes), append:

```kotlin
// Faint divider before RESET
Box(
    Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(Color.White.copy(alpha = 0.08f)),
)

SectionHeader("RESET")
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF161616))
        .clickable { showResetDialog = true }
        .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            "Reset to factory defaults",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Wipes all settings. Recordings & presets safe.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
        )
    }
    Text("→", color = MeatOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}
```

- [ ] **Step 4: Add the AlertDialog at the bottom of the outer Column**

After the entire scrollable `Column` closes (just before the outer `Column`'s closing brace at the end of the composable), add:

```kotlin
if (showResetDialog) {
    AlertDialog(
        onDismissRequest = { showResetDialog = false },
        title = {
            Text(
                "Reset all settings?",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                "Recordings, custom presets, and your theme won't be touched.",
                color = Color.White.copy(alpha = 0.75f),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.resetFactory()
                    showResetDialog = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = MeatOrange),
            ) {
                Text("Reset", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { showResetDialog = false }) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF161616),
    )
}
```

- [ ] **Step 5: Build to verify compile**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/MeatRecSettings.kt
git commit -m "feat(ui): add Reset to factory defaults section in Settings"
```

---

## Task 10: Manual verification (the only realistic integration test)

**Files:** None (manual app smoke test)

- [ ] **Step 1: Install on a device or emulator**

```bash
./gradlew :app:installDebug
```

- [ ] **Step 2: Persistence smoke test**

In the app:
1. Open Settings → tap a different theme (e.g., Forest) → confirm it changes.
2. Tap a non-48 kHz sample rate (e.g., 96 kHz).
3. Back to the recorder → toggle a few feature chips (AGC, hi-pass, anti-clip, compressor).
4. Open the menu → if there's a mic gain slider, move it off 0 dB.
5. Open EQ on any recording → adjust one band gain.
6. Force-stop the app: `adb shell am force-stop com.example.recorderproject`
7. Relaunch: `adb shell am start -n com.example.recorderproject/.MainActivity`

**Expected:**
- Theme remains the one you picked (this was already working via SharedPrefs).
- Sample rate pill in Settings still shows 96 kHz.
- Feature chips you toggled remain in the toggled state.
- Mic gain reads the value you set.
- Opening EQ shows the band gain you set.

- [ ] **Step 3: Factory reset smoke test**

1. Open Settings → scroll to bottom → tap "Reset to factory defaults".
2. Dialog appears → tap "Reset".

**Expected:**
- Toast: "Settings reset to defaults".
- Sample rate pill jumps to 48 kHz.
- Recorder Mode row reverts to CUSTOM.
- Theme is **unchanged** (stayed at Forest from Step 2).
- Back at the recorder, feature chips are all off, mic gain is 0 dB.
- Open EQ — chain is empty.
- The recordings list is unchanged (the WAV files are still there).
- Force-stop + relaunch → defaults still in place (proves the wipe persisted).

- [ ] **Step 4: SAF directory permission smoke test (skip if user never set a custom save directory)**

1. In Settings (or wherever the "Choose save location" button is), pick a folder via SAF.
2. Record something to confirm it lands there.
3. Force-stop + relaunch.
4. Record again — should land in the same folder without re-prompting.

- [ ] **Step 5: If all three pass — done. Otherwise debug, fix, commit, and re-test.**

---

## Self-Review Notes

**Spec coverage:**
- §Storage layout (DataStore wiped, SharedPrefs preserved) → Tasks 3, 8, 9 (dialog text matches "Recordings, custom presets, theme won't be touched").
- §Hydration & write-through (hydrated flag, debounced setters, recorder side effects, SAF URI) → Tasks 5, 6, 7.
- §Factory reset behavior (clear + applyDefaults + rewire + monitor teardown + EQ history clear + toast) → Task 8.
- §UI (RESET section + dialog + toast) → Task 9.
- §Things NOT persisted (monitor_enabled, runtime state, UI position) → Honored implicitly: those fields are never added as setters in Task 6.

**Type consistency:**
- `applyDefaults()`, `applySnapshot()`, `rewireRecorderFromState()`, `persistCurrentEqChain()`, `resetFactory()`, `hydrated`, `gainDbPersist`, `eqBandGainsPersist`, `settings` — all referenced names match across tasks.
- `SettingsSnapshot` field names match keys consumed in `applySnapshot` (Task 4) and produced in `snapshot()` (Task 3).

**Known approximations called out in the plan:**
- `RecordingQuality.valueOf` might not exist — Task 4 Step 2 includes the fallback instruction.
- The duplicate-import note in Task 4 Step 1 — acceptable because the file is large and grepping every existing import would bloat the plan.
- Tasks 4–8 verify by compile only, not unit test, because adding Robolectric is out of scope. This is explicit at the top of "File Structure".
