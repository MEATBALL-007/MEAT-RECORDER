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
import com.example.recorderproject.ui.EQScreen
import com.example.recorderproject.ui.OnboardingOverlay
import com.example.recorderproject.ui.RecorderApp
import com.example.recorderproject.ui.SettingsScreenV2
import com.example.recorderproject.ui.SplashScreen
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
    }

    private val directoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.setSaveDirectoryUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var splashDone by remember { mutableStateOf(false) }
            var onboardingDone by remember { mutableStateOf(getPreferences(MODE_PRIVATE).getBoolean("onboarding_done", false)) }
            var settingsOpen by remember { mutableStateOf(false) }
            var theme by remember { mutableStateOf("MEATrec") }
            var reduceMotion by remember { mutableStateOf(false) }
            RecorderProjectTheme(reduceMotion = reduceMotion) {
                val noiseReductionEnabled by viewModel.noiseReductionEnabled.collectAsStateWithLifecycle()
                val eqOpen by viewModel.eqOpen.collectAsStateWithLifecycle()
                when {
                    !splashDone -> SplashScreen(onDone = { splashDone = true })
                    !onboardingDone -> OnboardingOverlay(onDone = {
                        onboardingDone = true
                        getPreferences(MODE_PRIVATE).edit().putBoolean("onboarding_done", true).apply()
                    })
                    settingsOpen -> SettingsScreenV2(
                        theme = theme,
                        noiseReductionEnabled = noiseReductionEnabled,
                        reduceMotion = reduceMotion,
                        onChangeTheme = { theme = it },
                        onToggleNR = { viewModel.toggleNoiseReduction(it) },
                        onToggleReduceMotion = { reduceMotion = it },
                        onBack = { settingsOpen = false },
                    )
                    eqOpen -> EQScreen(
                        viewModel = viewModel,
                        onBack = { /* viewModel.onEQClose() already toggles eqOpen=false */ },
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
            }
        }
    }

    private fun requestRecordingPermissions() {
        val audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val storageGranted = !needsLegacyStoragePermission ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

        if (audioGranted && storageGranted) {
            Toast.makeText(this, "Starting recording...", Toast.LENGTH_SHORT).show()
            viewModel.startRecording()
        } else {
            Toast.makeText(this, "Requesting permissions...", Toast.LENGTH_SHORT).show()
            val perms = if (needsLegacyStoragePermission) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            permissionLauncher.launch(perms)
        }
    }

    private fun selectSaveDirectory() {
        directoryLauncher.launch(null)
    }
}
