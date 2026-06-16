package com.example.recorderproject.audio

import android.content.Context
import android.os.Environment
import com.example.recorderproject.data.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the take/scene/file naming state and rules extracted from RecorderViewModel
 * (issue #13 step 7c): the current file name, scene name, and notes, plus auto-naming,
 * take-number computation (disk scan), and the scene/take/subscene bump helpers.
 *
 * Behaviour moved verbatim (inline regex + direct Recordings-dir scan) — not refactored
 * onto the pure `RecordingNaming` helper, to keep this a behaviour-preserving extraction.
 *
 * @param app used to locate the Recordings directory for take-number scanning.
 */
class TakeNaming(
    private val app: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsDataStore,
    private val isHydrated: () -> Boolean,
) {
    private val _fileName = MutableStateFlow("scene1_take1.wav")
    val fileName: StateFlow<String> = _fileName

    private val _sceneName = MutableStateFlow("Scene 1")
    val sceneName: StateFlow<String> = _sceneName

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes

    // G15: Auto-name from scene + date/time when filename is left blank.
    fun autoNameForNextTake(): String {
        val now = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
            .format(java.util.Date())
        val scene = _sceneName.value.replace("[^A-Za-z0-9_-]".toRegex(), "_").take(24)
        val base = if (scene.isNotBlank()) "${scene}_$now" else "rec_$now"
        return "$base.wav"
    }

    private fun computeNextTakeNumber(sceneName: String): Int {
        val sanitized = sceneName.replace("[^A-Za-z0-9_.-]".toRegex(), "_")
        if (sanitized.isBlank()) return 1
        val dir = java.io.File(
            app.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            "Recordings",
        )
        if (!dir.exists()) return 1
        val pattern = Regex("^${Regex.escape(sanitized)}_T(\\d+)(_nr)?\\.wav$", RegexOption.IGNORE_CASE)
        val existing = dir.listFiles()?.mapNotNull { f ->
            pattern.matchEntire(f.name)?.groupValues?.get(1)?.toIntOrNull()
        } ?: emptyList()
        return (existing.maxOrNull() ?: 0) + 1
    }

    /** Update the default file name based on current scene + next take number. */
    fun refreshAutoFileName() {
        val scene = _sceneName.value.trim()
        if (scene.isBlank()) return
        val sanitized = scene.replace("[^A-Za-z0-9_.-]".toRegex(), "_")
        val takeNum = computeNextTakeNumber(scene)
        val newName = "${sanitized}_T${"%02d".format(takeNum)}.wav"
        _fileName.value = newName
    }

    fun updateFileName(value: String) {
        _fileName.value = value
    }

    fun updateSceneName(value: String) {
        _sceneName.value = value
        if (isHydrated()) scope.launch { settings.setSceneName(value) }
        refreshAutoFileName()
    }

    /**
     * H: Manually adjust take number by [delta].
     * Parses the trailing _T## in the current filename and adds delta (clamped to >=1).
     * Useful to skip a take (+1) or back up to overwrite (-1).
     */
    fun bumpTake(delta: Int = 1) {
        val current = _fileName.value
        val match = Regex("^(.+)_T(\\d+)(\\.wav)?$", RegexOption.IGNORE_CASE).matchEntire(current)
        if (match != null) {
            val base = match.groupValues[1]
            val n = ((match.groupValues[2].toIntOrNull() ?: 0) + delta).coerceAtLeast(1)
            val ext = match.groupValues[3].ifBlank { ".wav" }
            _fileName.value = "${base}_T${"%02d".format(n)}$ext"
        } else {
            refreshAutoFileName()
        }
    }

    /**
     * H: Manually bump scene to next decimal sub-scene (default +0.1).
     * "Scene 1" + 0.1 → "Scene 1.1" → +0.1 → "Scene 1.2" … ; take resets to 01.
     * Negative delta steps backward (clamps at .0, which collapses back to the
     * integer-only form, e.g. "Scene 1.1" - 0.1 → "Scene 1").
     */
    fun bumpSubscene(deltaTenths: Int = 1) {
        val base = _sceneName.value.trim()
        val m = Regex("^(.*?)(\\d+)(?:\\.(\\d+))?\\s*$").matchEntire(base)
        val nextScene = if (m != null) {
            val prefix = m.groupValues[1]
            val whole = m.groupValues[2]
            val frac = (m.groupValues[3].toIntOrNull() ?: 0) + deltaTenths
            when {
                frac > 0 -> "$prefix$whole.$frac"
                frac == 0 -> "$prefix$whole"
                else -> "$prefix$whole"     // can't go below 0; stay at integer form
            }
        } else if (deltaTenths > 0) {
            "$base.$deltaTenths"
        } else {
            base
        }
        _sceneName.value = nextScene
        if (isHydrated()) scope.launch { settings.setSceneName(nextScene) }
        refreshAutoFileName()
    }

    /**
     * H: Manually bump the whole-number scene (default +1).
     * "Scene 1" + 1 → "Scene 2"; "Scene 1.3" + 1 → "Scene 2" (fractional resets);
     * negative delta clamps at 1.
     */
    fun bumpScene(delta: Int) {
        val base = _sceneName.value.trim()
        val m = Regex("^(.*?)(\\d+)(?:\\.\\d+)?\\s*$").matchEntire(base)
        val nextScene = if (m != null) {
            val prefix = m.groupValues[1]
            val whole = (m.groupValues[2].toIntOrNull() ?: 1) + delta
            "$prefix${whole.coerceAtLeast(1)}"
        } else {
            "$base ${(1 + delta).coerceAtLeast(1)}"
        }
        _sceneName.value = nextScene
        if (isHydrated()) scope.launch { settings.setSceneName(nextScene) }
        refreshAutoFileName()
    }

    fun updateNotes(value: String) {
        _notes.value = value
    }

    /** Hydrate the persisted scene name from a snapshot (in-memory only). */
    fun applySnapshot(sceneName: String) {
        _sceneName.value = sceneName
    }
}
