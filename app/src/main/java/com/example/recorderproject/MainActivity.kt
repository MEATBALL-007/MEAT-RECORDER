package com.example.recorderproject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recorderproject.ui.AbCompareScreen
import com.example.recorderproject.ui.EQScreen
import com.example.recorderproject.ui.HarmonicPortraitScreen
import com.example.recorderproject.ui.DesignPickerScreen
import com.example.recorderproject.ui.MeatRecModeSelector
import com.example.recorderproject.ui.MeatRecSettings
import com.example.recorderproject.ui.MenuScreen
import com.example.recorderproject.ui.MultiTakeScreen
import com.example.recorderproject.ui.OnboardingOverlay
import com.example.recorderproject.ui.RecorderApp
import com.example.recorderproject.ui.RecorderAppWithIntro
import com.example.recorderproject.ui.RoomProfilerScreen
import com.example.recorderproject.ui.SceneSlicerScreen
import com.example.recorderproject.ui.SettingsScreenV2
import com.example.recorderproject.ui.StatisticsScreen
import com.example.recorderproject.ui.TranscriptScreen
import com.example.recorderproject.ui.TrimScreen
import com.example.recorderproject.ui.components.PitchShiftDialog
import com.example.recorderproject.ui.theme.AppTheme
import com.example.recorderproject.ui.theme.RecorderProjectTheme
import android.widget.Toast

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<RecorderViewModel>()

    // Storage permission is a runtime permission only on API <= 28 (legacy storage).
    // From API 29 onward, scoped storage / app-specific dirs need no permission, and
    // WRITE_EXTERNAL_STORAGE is silently denied if requested.
    private val needsLegacyStoragePermission =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val storageGranted = !needsLegacyStoragePermission ||
            (permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false)
        if (audioGranted && storageGranted) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            viewModel.onPermissionGranted()
            viewModel.startRecording()
        } else {
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_LONG).show()
            viewModel.onPermissionDenied()
        }
        // POST_NOTIFICATIONS denial is intentionally not blocking — foreground service
        // still works; the user just won't see the recording notification.
    }

    private val directoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.setSaveDirectoryUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var onboardingDone by remember { mutableStateOf(getPreferences(MODE_PRIVATE).getBoolean("onboarding_done", false)) }
            var modeChosen by remember { mutableStateOf(getPreferences(MODE_PRIVATE).getBoolean("mode_chosen", false)) }
            var settingsOpen by remember { mutableStateOf(false) }
            // Persist theme by display name across launches so user's pick survives restart.
            val savedThemeName = getPreferences(MODE_PRIVATE).getString("app_theme", null)
            var appTheme by remember { mutableStateOf(AppTheme.fromName(savedThemeName)) }
            var reduceMotion by remember { mutableStateOf(false) }
            RecorderProjectTheme(appTheme = appTheme, reduceMotion = reduceMotion) {
                val eqOpen by viewModel.eqOpen.collectAsStateWithLifecycle()
                val portraitFile by viewModel.portraitFile.collectAsStateWithLifecycle()
                val abCompareOpen by viewModel.abCompareOpen.collectAsStateWithLifecycle()
                val multiTakeOpen by viewModel.multiTakeOpen.collectAsStateWithLifecycle()
                val transcriptFile by viewModel.transcriptFile.collectAsStateWithLifecycle()
                val pitchShiftFile by viewModel.pitchShiftFile.collectAsStateWithLifecycle()
                val roomProfilerOpen by viewModel.roomProfilerOpen.collectAsStateWithLifecycle()
                val sceneSliceFile by viewModel.sceneSliceFile.collectAsStateWithLifecycle()
                val menuOpen by viewModel.menuOpen.collectAsStateWithLifecycle()
                val statsOpen by viewModel.statsOpen.collectAsStateWithLifecycle()
                val trimFile by viewModel.trimFile.collectAsStateWithLifecycle()
                val designPickerOpen by viewModel.designPickerOpen.collectAsStateWithLifecycle()
                // RecorderAppWithIntro plays the fade+scale splash before revealing whatever
                // route is active — port-back of old MEATrec intro wrapper API.
                RecorderAppWithIntro {
                    when {
                        !modeChosen -> MeatRecModeSelector(
                            onSelectMode = { mode ->
                                viewModel.selectRecorderMode(mode)
                                modeChosen = true
                                getPreferences(MODE_PRIVATE).edit().putBoolean("mode_chosen", true).apply()
                            },
                            onSkip = {
                                // Default-on-skip is now CUSTOM (was INTERVIEW)
                                viewModel.selectRecorderMode(com.example.recorderproject.model.RecorderMode.CUSTOM)
                                modeChosen = true
                                getPreferences(MODE_PRIVATE).edit().putBoolean("mode_chosen", true).apply()
                            },
                            onCreatePreset = { name ->
                                // Save the preset name + lock in CUSTOM mode + go home.
                                // Full preset storage (gains, NR, etc.) is a follow-up;
                                // for now we persist the chosen name so it's recoverable.
                                viewModel.selectRecorderMode(com.example.recorderproject.model.RecorderMode.CUSTOM)
                                val prefs = getPreferences(MODE_PRIVATE)
                                val existing = prefs.getStringSet("custom_presets", emptySet()) ?: emptySet()
                                val merged = (existing + name).toSet()
                                prefs.edit()
                                    .putStringSet("custom_presets", merged)
                                    .putString("active_preset", name)
                                    .putBoolean("mode_chosen", true)
                                    .apply()
                                modeChosen = true
                                Toast.makeText(this, "Saved preset: $name", Toast.LENGTH_SHORT).show()
                            },
                        )
                        !onboardingDone -> OnboardingOverlay(onDone = {
                            onboardingDone = true
                            getPreferences(MODE_PRIVATE).edit().putBoolean("onboarding_done", true).apply()
                        })
                        settingsOpen -> MeatRecSettings(
                            viewModel = viewModel,
                            currentTheme = appTheme,
                            onChangeTheme = {
                                appTheme = it
                                getPreferences(MODE_PRIVATE).edit()
                                    .putString("app_theme", it.displayName).apply()
                            },
                            onBack = { settingsOpen = false },
                            onChangeMode = {
                                // Reset the mode-chosen flag → next render shows the selector again
                                modeChosen = false
                                getPreferences(MODE_PRIVATE).edit()
                                    .putBoolean("mode_chosen", false).apply()
                                settingsOpen = false
                            },
                        )
                        eqOpen -> EQScreen(
                            viewModel = viewModel,
                            onBack = { /* viewModel.onEQClose() already toggles eqOpen=false */ },
                        )
                        portraitFile != null -> HarmonicPortraitScreen(
                            file = portraitFile!!,
                            onBack = { viewModel.closePortrait() },
                        )
                        abCompareOpen -> AbCompareScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.closeAbCompare() },
                        )
                        multiTakeOpen -> MultiTakeScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.closeMultiTake() },
                        )
                        transcriptFile != null -> TranscriptScreen(
                            viewModel = viewModel,
                            file = transcriptFile!!,
                            onBack = { viewModel.closeTranscript() },
                        )
                        roomProfilerOpen -> RoomProfilerScreen(
                            onBack = { viewModel.closeRoomProfiler() },
                        )
                        sceneSliceFile != null -> SceneSlicerScreen(
                            file = sceneSliceFile!!,
                            onBack = { viewModel.closeSceneSlicer() },
                        )
                        menuOpen -> MenuScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.closeMenu() },
                            onOpenSettings = { viewModel.closeMenu(); settingsOpen = true },
                            onOpenEQOnLast = {
                                viewModel.closeMenu()
                                val last = viewModel.recordFiles.value.lastOrNull()
                                if (last != null) viewModel.onEQOpen(last)
                                else Toast.makeText(this, "No recordings yet", Toast.LENGTH_SHORT).show()
                            },
                            onOpenRoomProfiler = { viewModel.closeMenu(); viewModel.openRoomProfiler() },
                            onOpenMultiTake = { viewModel.closeMenu(); viewModel.openMultiTake() },
                            onOpenStats = { viewModel.closeMenu(); viewModel.openStats() },
                            onOpenDesignPicker = { viewModel.closeMenu(); viewModel.openDesignPicker() },
                        )
                        statsOpen -> StatisticsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.closeStats() },
                        )
                        designPickerOpen -> DesignPickerScreen(
                            onBack = { viewModel.closeDesignPicker() },
                        )
                        trimFile != null -> TrimScreen(
                            file = trimFile!!,
                            onConfirm = { inMs, outMs ->
                                Toast.makeText(
                                    this,
                                    "Trim ${inMs}ms..${outMs}ms (export wiring pending)",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                viewModel.closeTrim()
                            },
                            onBack = { viewModel.closeTrim() },
                        )
                        else -> RecorderApp(
                            viewModel = viewModel,
                            onStartRecording = { requestRecordingPermissions() },
                            onSelectSaveLocation = { selectSaveDirectory() },
                            onRequestPermission = { requestRecordingPermissions() },
                            onOpenEQOnLast = {
                                val last = viewModel.recordFiles.value.lastOrNull()
                                if (last != null) {
                                    viewModel.onEQOpen(last)
                                } else {
                                    Toast.makeText(this, "No recordings yet — record something first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onOpenSettings = { settingsOpen = true },
                        )
                    }

                    // Phase E pitch-shift dialog — floats over the current route
                    // (typically RecorderApp). Closes itself on apply/cancel.
                    pitchShiftFile?.let { f ->
                        PitchShiftDialog(
                            file = f,
                            onApply = { semitones ->
                                val out = viewModel.shiftFilePitch(f, semitones)
                                Toast.makeText(
                                    this,
                                    if (out != null) "Saved ${out.name}" else "Pitch shift failed",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onDismiss = { viewModel.closePitchShift() },
                        )
                    }
                }
            }
        }
    }

    private fun requestRecordingPermissions() {
        val audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val storageGranted = !needsLegacyStoragePermission ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        val notificationsGranted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        if (audioGranted && storageGranted && notificationsGranted) {
            Toast.makeText(this, "Starting recording...", Toast.LENGTH_SHORT).show()
            viewModel.startRecording()
        } else {
            Toast.makeText(this, "Requesting permissions...", Toast.LENGTH_SHORT).show()
            val perms = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (needsLegacyStoragePermission) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
            permissionLauncher.launch(perms)
        }
    }

    private fun selectSaveDirectory() {
        directoryLauncher.launch(null)
    }
}
