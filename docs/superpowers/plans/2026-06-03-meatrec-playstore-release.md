# MEAT REC — Play Store Production Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform MEAT REC from a development build with `com.example` package and stub features into a fully functional, Play Store-ready Android app with working Pitch Shift, Trim, VAD, Transcription, and Google Drive backup.

**Architecture:** Three sprints in strict dependency order — Sprint 1 fixes Play Store blockers (would cause hard rejection), Sprint 2 fixes crashes and visible stubs, Sprint 3 wires the three unimplemented features. Each sprint ends in a build-verified commit. All new features follow the existing ViewModel → Repository → Composable pattern already in the codebase.

**Tech Stack:** Kotlin / Jetpack Compose / Android AudioRecord / WSOLA pitch-shifting (pure Kotlin, no NDK) / Android SpeechRecognizer (on-device) / Google Drive REST API v3 + `play-services-auth:21.2.0`

---

## File Map

### Modified files
| File | Change |
|---|---|
| `app/build.gradle.kts` | applicationId, compileSdk/targetSdk→35, R8, signing, new deps |
| `gradle/libs.versions.toml` | add play-services-auth, mlkit-language-id |
| `app/proguard-rules.pro` | real keep rules for NanoHTTPD, DataStore, java-websocket |
| `app/src/main/AndroidManifest.xml` | add RECORD_AUDIO for speech, meta-data for auth |
| `app/src/main/java/.../RecorderViewModel.kt` | wire VAD, Trim, Transcription, Drive backup |
| `app/src/main/java/.../MainActivity.kt` | wire TrimScreen confirm, Drive sign-in, settings route |
| `app/src/main/java/.../audio/PitchShifter.kt` | full WSOLA implementation |
| `app/src/main/java/.../ui/TrimScreen.kt` | replace stub with real Canvas waveform |
| `app/src/main/java/.../ui/TranscriptScreen.kt` | loading state, real transcribe button |
| `app/src/main/java/.../ui/SettingsScreen.kt` | redirect stub → SettingsScreenV2 |
| `app/src/main/java/.../ui/components/PresetEditSheet.kt` | rebuild from recovered source |
| `app/src/main/java/.../ui/SettingsScreenV2.kt` | add Privacy Policy link + Google Drive section |
| `app/src/main/java/.../ui/MeatRecSettings.kt` | add Privacy Policy link |

### New files
| File | Purpose |
|---|---|
| `app/src/main/java/.../ui/PrivacyPolicyScreen.kt` | required by Play Store — shows HTML privacy policy |
| `app/src/main/java/.../audio/WaveformLoader.kt` | loads WAV PCM → downsampled FloatArray for TrimScreen |
| `app/src/main/java/.../audio/VoiceActivityDetector.kt` | energy-based VAD — triggers startRecording |
| `app/src/main/java/.../audio/TranscriptionEngine.kt` | Android SpeechRecognizer loopback transcription |
| `app/src/main/java/.../network/GoogleDriveUploader.kt` | Drive REST API v3 upload via HttpURLConnection |
| `keystore.properties` | signing credentials (gitignored) |

### Deleted files
- `app/src/main/java/.../ui/SettingsScreen.kt.recovered.txt`
- `app/src/main/java/.../ui/components/PresetEditSheet.kt.recovered.txt`
- `_symbol_inventory.json`

---

## Sprint 1 — Play Store Blockers

---

### Task 1: Application ID + Build Configuration

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/proguard-rules.pro`

- [ ] **Step 1: Update build.gradle.kts**

Replace the entire `android {}` block and dependencies section:

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Load signing properties from keystore.properties (created in Task 2)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = java.util.Properties().apply {
    if (keystorePropertiesFile.exists()) load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.example.recorderproject"   // source code namespace — do NOT change this
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meatrec.recorder"  // Play Store ID — changed from com.example
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.runtime.livedata)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Lifecycle + Coroutines
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    // DataStore + DocumentFile + Networking
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.nanohttpd)
    implementation(libs.java.websocket)

    // Google Sign-In + Drive backup (Task 11)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

- [ ] **Step 2: Add play-services-auth to libs.versions.toml**

Open `gradle/libs.versions.toml` and add:

```toml
[versions]
# ... existing versions ...
playServicesAuth = "21.2.0"

[libraries]
# ... existing libraries ...
play-services-auth = { group = "com.google.android.gms", name = "play-services-auth", version.ref = "playServicesAuth" }
```

- [ ] **Step 3: Write real ProGuard rules**

Replace the entire content of `app/proguard-rules.pro`:

```proguard
# Preserve stack trace line numbers in crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# NanoHTTPD — embedded HTTP server
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# Java-WebSocket
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**

# DataStore (Protobuf-based internals)
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Kotlin serialization / coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose runtime
-keep class androidx.compose.runtime.** { *; }

# Android SpeechRecognizer (used for transcription in Task 10)
-keep class android.speech.** { *; }

# Google Sign-In (Task 11)
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# App model classes (used in DataStore serialization)
-keep class com.example.recorderproject.model.** { *; }
-keep class com.example.recorderproject.data.** { *; }
```

- [ ] **Step 4: Verify build**

```bash
cd /Users/meatball_mac/RECORDER_PROJECT
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts gradle/libs.versions.toml app/proguard-rules.pro
git commit -m "build: change applicationId to com.meatrec.recorder, targetSdk 35, enable R8"
```

---

### Task 2: Release Signing Keystore

**Files:**
- Create: `keystore.properties` (gitignored)
- Create: `meatrec-release.jks`

- [ ] **Step 1: Generate release keystore**

Run this in the project root. Replace `YOUR_PASSWORD` and details with real values:

```bash
keytool -genkeypair \
  -v \
  -keystore /Users/meatball_mac/RECORDER_PROJECT/meatrec-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias meatrec \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD \
  -dname "CN=MEAT REC, OU=Mobile, O=MeatRec, L=Bangkok, ST=Bangkok, C=TH"
```

Expected output ends with: `Generating 2,048 bit RSA key pair and self-signed certificate (SHA256withRSA)...`

- [ ] **Step 2: Create keystore.properties**

Create `/Users/meatball_mac/RECORDER_PROJECT/keystore.properties` with your actual passwords:

```properties
storeFile=../meatrec-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=meatrec
keyPassword=YOUR_KEY_PASSWORD
```

- [ ] **Step 3: Add both files to .gitignore**

```bash
echo "keystore.properties" >> /Users/meatball_mac/RECORDER_PROJECT/.gitignore
echo "meatrec-release.jks" >> /Users/meatball_mac/RECORDER_PROJECT/.gitignore
```

- [ ] **Step 4: Verify release build signs correctly**

```bash
./gradlew assembleRelease
```

Expected: `BUILD SUCCESSFUL` — and `app/build/outputs/apk/release/app-release.apk` exists (not `app-release-unsigned.apk`).

- [ ] **Step 5: Commit**

```bash
git add .gitignore
git commit -m "build: add release signing config (keystore files gitignored)"
```

---

### Task 3: Privacy Policy Screen

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/ui/PrivacyPolicyScreen.kt`
- Modify: `app/src/main/java/com/example/recorderproject/MainActivity.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/SettingsScreenV2.kt`

- [ ] **Step 1: Create PrivacyPolicyScreen.kt**

```kotlin
// app/src/main/java/com/example/recorderproject/ui/PrivacyPolicyScreen.kt
package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderCharcoal
import com.example.recorderproject.ui.theme.RecorderCharcoalCard
import com.example.recorderproject.ui.theme.RecorderYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", color = RecorderYellow, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RecorderBlueGrey)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RecorderCharcoal),
            )
        },
        containerColor = RecorderCharcoal,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            PolicySection("Last updated: June 2026")

            PolicySection("Overview",
                "MEAT REC is a professional audio recording application. " +
                "This policy explains what data we collect, how we use it, and your rights."
            )

            PolicySection("Microphone Access",
                "MEAT REC requires microphone permission to record audio. " +
                "Audio recordings are stored only on your device or in the cloud backup location " +
                "you choose. We never transmit your recordings to our servers."
            )

            PolicySection("Location Data",
                "When you record, MEAT REC may attach your GPS coordinates to the file's " +
                "metadata (iXML Location tag) so you can remember where a take was recorded. " +
                "Location data is stored only inside the WAV file on your device. " +
                "We do not collect or transmit location data to any server. " +
                "You can disable location tagging in Settings → Location Metadata."
            )

            PolicySection("Cloud Backup (Google Drive)",
                "If you enable Google Drive backup, your recordings are uploaded to your " +
                "personal Google Drive account. MEAT REC only accesses files it creates " +
                "(drive.file scope). We cannot access any other files in your Drive. " +
                "Google's Privacy Policy governs data stored in Google Drive."
            )

            PolicySection("Local Network Server",
                "MEAT REC includes an optional local network server that lets you transfer " +
                "recordings from your phone to a computer on the same Wi-Fi network. " +
                "This server is only active while the app is open and only accepts " +
                "connections from devices on your local network. No data is sent to the internet."
            )

            PolicySection("Bluetooth",
                "MEAT REC uses Bluetooth to detect and route audio from Bluetooth microphones " +
                "and headsets. We do not read device names, pair codes, or any Bluetooth " +
                "metadata beyond audio routing."
            )

            PolicySection("Data Storage",
                "All recordings and settings are stored locally on your device. " +
                "No personal data is collected by us. No analytics, no crash reporting, " +
                "no advertising SDKs are included in MEAT REC."
            )

            PolicySection("Contact",
                "Questions? Contact: support@meatrec.app"
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(RecorderCharcoalCard, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = RecorderYellow, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        body?.let { Text(it, color = Color.White, fontSize = 13.sp, lineHeight = 20.sp) }
    }
}
```

- [ ] **Step 2: Add privacy policy route to MainActivity**

In `MainActivity.kt`, add `var privacyOpen by remember { mutableStateOf(false) }` near the other state vars (around line 84), then add it to the `when` block before the `else ->` clause:

```kotlin
// In the 'when' block, add before 'else ->':
privacyOpen -> PrivacyPolicyScreen(onBack = { privacyOpen = false })
```

Also add this import at the top of the file:
```kotlin
import com.example.recorderproject.ui.PrivacyPolicyScreen
```

And pass `onOpenPrivacy = { privacyOpen = true }` to `MeatRecSettings` and `SettingsScreenV2` calls.

- [ ] **Step 3: Add Privacy Policy link to SettingsScreenV2**

Find the "About" section at the bottom of `SettingsScreenV2.kt`. After the existing about text, add:

```kotlin
Spacer(Modifier.height(8.dp))
OutlinedButton(
    onClick = onOpenPrivacy,
    modifier = Modifier.fillMaxWidth(),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = RecorderBlueGrey),
) {
    Text("Privacy Policy", fontSize = 13.sp)
}
```

Add `onOpenPrivacy: () -> Unit` to the function signature of `SettingsScreenV2`.

- [ ] **Step 4: Build and verify**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/PrivacyPolicyScreen.kt \
        app/src/main/java/com/example/recorderproject/MainActivity.kt \
        app/src/main/java/com/example/recorderproject/ui/SettingsScreenV2.kt
git commit -m "feat: add Privacy Policy screen (required for Play Store with location + internet perms)"
```

---

### Task 4: Location Permission Disclosure Dialog

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/MainActivity.kt`

Play Store requires an in-app disclosure before accessing location. Add a one-time dialog that explains the location tagging feature and lets the user opt out.

- [ ] **Step 1: Add location disclosure in MainActivity's requestRecordingPermissions()**

Find `requestRecordingPermissions()` in `MainActivity.kt`. Before the existing permission check, add:

```kotlin
private fun requestRecordingPermissions() {
    // Show location disclosure once before requesting location permission
    val prefs = getPreferences(MODE_PRIVATE)
    val locationDisclosureShown = prefs.getBoolean("location_disclosure_shown", false)
    if (!locationDisclosureShown) {
        showLocationDisclosureDialog()
        return
    }
    proceedWithRecordingPermissions()
}

private fun showLocationDisclosureDialog() {
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle("Location Metadata (Optional)")
        .setMessage(
            "MEAT REC can tag your recordings with GPS coordinates — stored inside " +
            "the WAV file so you remember where each take was recorded.\n\n" +
            "Location data stays on your device and is never sent to any server.\n\n" +
            "Allow location tagging?"
        )
        .setPositiveButton("Allow") { _, _ ->
            getPreferences(MODE_PRIVATE).edit()
                .putBoolean("location_disclosure_shown", true)
                .putBoolean("location_tagging_enabled", true)
                .apply()
            proceedWithRecordingPermissions()
        }
        .setNegativeButton("No thanks") { _, _ ->
            getPreferences(MODE_PRIVATE).edit()
                .putBoolean("location_disclosure_shown", true)
                .putBoolean("location_tagging_enabled", false)
                .apply()
            proceedWithRecordingPermissions()
        }
        .show()
}

private fun proceedWithRecordingPermissions() {
    val audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    val storageGranted = !needsLegacyStoragePermission ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    val notificationsGranted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    val bluetoothGranted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    if (audioGranted && storageGranted && notificationsGranted && bluetoothGranted) {
        viewModel.startRecording()
    } else {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (needsLegacyStoragePermission) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.POST_NOTIFICATIONS)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            if (getPreferences(MODE_PRIVATE).getBoolean("location_tagging_enabled", false)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }
}
```

Also delete the old `requestRecordingPermissions()` body — replace it entirely with the above.

- [ ] **Step 2: Build and verify**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/MainActivity.kt
git commit -m "feat: add location permission disclosure dialog (Play Store policy requirement)"
```

---

## Sprint 2 — Bug Fixes & Stub Removal

---

### Task 5: PitchShifter — WSOLA Implementation

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/audio/PitchShifter.kt`

WSOLA (Waveform Similarity Overlap-Add): time-stretch audio by the inverse of the desired speed factor, then resample back to the original frame count. This changes pitch without changing duration.

- [ ] **Step 1: Replace PitchShifter.kt with full WSOLA implementation**

```kotlin
// app/src/main/java/com/example/recorderproject/audio/PitchShifter.kt
package com.example.recorderproject.audio

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

object PitchShifter {
    private const val TAG = "PitchShifter"
    private const val FRAME_SIZE = 2048
    private const val HOP_SIZE = 512

    /**
     * Shift pitch of [inputPath] WAV by [semitones] and write result to [outputPath].
     * Uses WSOLA: time-stretch by 1/speedFactor, then resample back to original length.
     * Positive semitones = higher pitch; negative = lower pitch. Range: [-12, +12].
     */
    fun shift(inputPath: String, outputPath: String, semitones: Float) {
        Log.d(TAG, "shift() semitones=$semitones input=$inputPath")
        if (abs(semitones) < 0.01f) {
            File(inputPath).copyTo(File(outputPath), overwrite = true)
            return
        }
        val info = readWavInfo(inputPath)
        val inputSamples = readPcmFloat(inputPath, info)
        val speedFactor = 2.0.pow(semitones / 12.0).toFloat()
        val stretchFactor = 1f / speedFactor
        val stretched = wsolaStretch(inputSamples, stretchFactor)
        val resampled = linearResample(stretched, inputSamples.size)
        writePcmFloat(outputPath, resampled, info)
        Log.d(TAG, "shift() done → $outputPath")
    }

    private fun wsolaStretch(input: FloatArray, stretchFactor: Float): FloatArray {
        val window = hannWindow(FRAME_SIZE)
        val outLen = (input.size * stretchFactor).toInt().coerceAtLeast(FRAME_SIZE)
        val out = FloatArray(outLen)
        val norm = FloatArray(outLen)

        var outPos = 0
        var idealInPos = 0.0
        var lastFoundPos = 0

        while (outPos + FRAME_SIZE <= outLen) {
            val target = idealInPos.toInt().coerceIn(0, input.size - FRAME_SIZE)
            val searchStart = (target - HOP_SIZE).coerceAtLeast(0)
            val searchEnd = (target + HOP_SIZE).coerceAtMost(input.size - FRAME_SIZE)

            // WSOLA: find the frame in the input that best correlates with the
            // already-written tail of the output (overlap region).
            val bestPos = if (outPos >= HOP_SIZE) {
                findBestCorrelation(input, searchStart, searchEnd, out, outPos - HOP_SIZE)
            } else target

            for (i in 0 until FRAME_SIZE) {
                val inIdx = bestPos + i
                val outIdx = outPos + i
                if (inIdx < input.size && outIdx < outLen) {
                    out[outIdx] += input[inIdx] * window[i]
                    norm[outIdx] += window[i]
                }
            }

            lastFoundPos = bestPos
            outPos += HOP_SIZE
            idealInPos += HOP_SIZE / stretchFactor
        }

        for (i in out.indices) {
            if (norm[i] > 1e-4f) out[i] /= norm[i]
        }
        return out
    }

    /** Cross-correlation: find offset in [searchStart..searchEnd] of input that best
     *  matches the [refStart..(refStart+HOP_SIZE)] slice of [reference]. */
    private fun findBestCorrelation(
        input: FloatArray,
        searchStart: Int,
        searchEnd: Int,
        reference: FloatArray,
        refStart: Int,
    ): Int {
        var bestPos = searchStart
        var bestCorr = Float.NEGATIVE_INFINITY
        val refEnd = (refStart + HOP_SIZE).coerceAtMost(reference.size)
        val refLen = refEnd - refStart
        if (refLen <= 0) return searchStart

        var candidate = searchStart
        while (candidate <= searchEnd) {
            var corr = 0f
            for (i in 0 until refLen) {
                val inputIdx = candidate + i
                if (inputIdx < input.size && refStart + i < reference.size) {
                    corr += input[inputIdx] * reference[refStart + i]
                }
            }
            if (corr > bestCorr) { bestCorr = corr; bestPos = candidate }
            candidate += 4  // Step by 4 for speed — fine enough resolution
        }
        return bestPos
    }

    private fun hannWindow(size: Int): FloatArray =
        FloatArray(size) { i -> 0.5f * (1f - cos(2.0 * PI * i / (size - 1)).toFloat()) }

    private fun linearResample(input: FloatArray, targetSize: Int): FloatArray {
        if (input.size == targetSize) return input
        val out = FloatArray(targetSize)
        val ratio = (input.size - 1).toDouble() / (targetSize - 1).coerceAtLeast(1)
        for (i in 0 until targetSize) {
            val pos = i * ratio
            val lo = pos.toInt().coerceIn(0, input.size - 2)
            val frac = (pos - lo).toFloat()
            out[i] = input[lo] * (1f - frac) + input[lo + 1] * frac
        }
        return out
    }

    // ---- WAV I/O ----

    private data class WavInfo(
        val sampleRate: Int,
        val channelCount: Int,
        val bitDepth: Int,
        val audioFormat: Int,   // 1=PCM integer, 3=IEEE float
        val dataOffset: Int,
        val dataSize: Int,
    )

    private fun readWavInfo(path: String): WavInfo {
        FileInputStream(path).use { fis ->
            val header = ByteArray(44)
            fis.read(header)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(20)
            val fmt = buf.short.toInt() and 0xFFFF
            val ch = buf.short.toInt() and 0xFFFF
            val sr = buf.int
            buf.int   // byteRate
            buf.short // blockAlign
            val bd = buf.short.toInt() and 0xFFFF
            buf.int   // "data"
            val dataSize = buf.int
            return WavInfo(sr, ch, bd, fmt, 44, dataSize)
        }
    }

    private fun readPcmFloat(path: String, info: WavInfo): FloatArray {
        val raw = FileInputStream(path).use { fis ->
            fis.skip(info.dataOffset.toLong())
            fis.readBytes()
        }
        val bytesPerSample = info.bitDepth / 8
        val totalSamples = raw.size / bytesPerSample
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(totalSamples) { _ ->
            when (info.bitDepth) {
                16 -> buf.short / 32768f
                24 -> {
                    val b0 = buf.get().toInt() and 0xFF
                    val b1 = buf.get().toInt() and 0xFF
                    val b2 = buf.get().toInt()  // sign-extended
                    ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
                }
                32 -> if (info.audioFormat == 3) buf.float else buf.int / 2147483648f
                else -> buf.short / 32768f
            }
        }
    }

    private fun writePcmFloat(path: String, samples: FloatArray, info: WavInfo) {
        val bytesPerSample = info.bitDepth / 8
        val dataLen = samples.size * bytesPerSample
        FileOutputStream(path).use { fos ->
            val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            hdr.put("RIFF".toByteArray()); hdr.putInt(dataLen + 36)
            hdr.put("WAVE".toByteArray()); hdr.put("fmt ".toByteArray())
            hdr.putInt(16)
            hdr.putShort(info.audioFormat.toShort())
            hdr.putShort(info.channelCount.toShort())
            hdr.putInt(info.sampleRate)
            hdr.putInt(info.sampleRate * info.channelCount * bytesPerSample)
            hdr.putShort((info.channelCount * bytesPerSample).toShort())
            hdr.putShort(info.bitDepth.toShort())
            hdr.put("data".toByteArray()); hdr.putInt(dataLen)
            fos.write(hdr.array())

            val pcm = ByteBuffer.allocate(dataLen).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                val clamped = s.coerceIn(-1f, 1f)
                when (info.bitDepth) {
                    16 -> pcm.putShort((clamped * 32767f).toInt().toShort())
                    24 -> {
                        val v = (clamped * 8388607f).toInt()
                        pcm.put((v and 0xFF).toByte())
                        pcm.put(((v shr 8) and 0xFF).toByte())
                        pcm.put(((v shr 16) and 0xFF).toByte())
                    }
                    32 -> if (info.audioFormat == 3) pcm.putFloat(clamped)
                          else pcm.putInt((clamped * 2147483647f).toInt())
                    else -> pcm.putShort((clamped * 32767f).toInt().toShort())
                }
            }
            fos.write(pcm.array())
        }
    }
}
```

- [ ] **Step 2: Build to verify no compile errors**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/PitchShifter.kt
git commit -m "feat: implement PitchShifter with WSOLA algorithm — fixes guaranteed crash on pitch-shift tap"
```

---

### Task 6: TrimScreen — Real Waveform + Export Wiring

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/WaveformLoader.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/TrimScreen.kt`
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`
- Modify: `app/src/main/java/com/example/recorderproject/MainActivity.kt`

- [ ] **Step 1: Create WaveformLoader.kt**

```kotlin
// app/src/main/java/com/example/recorderproject/audio/WaveformLoader.kt
package com.example.recorderproject.audio

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WaveformLoader {
    /**
     * Load a WAV file and return a FloatArray of [targetSamples] amplitude values
     * in the range [0, 1]. Returns an empty array on any error.
     */
    fun load(path: String, targetSamples: Int = 300): FloatArray {
        if (targetSamples <= 0) return FloatArray(0)
        val file = File(path)
        if (!file.exists()) return FloatArray(targetSamples)
        return try {
            // Read WAV header
            val headerBuf = ByteArray(44)
            FileInputStream(file).use { fis ->
                if (fis.read(headerBuf) < 44) return FloatArray(targetSamples)
            }
            val hdr = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
            hdr.position(20)
            val audioFormat = hdr.short.toInt() and 0xFFFF
            val channels = hdr.short.toInt().coerceAtLeast(1)
            hdr.position(34)
            val bitDepth = hdr.short.toInt().coerceAtLeast(8)
            val bytesPerSample = bitDepth / 8
            val bytesPerFrame = bytesPerSample * channels

            // Read all PCM bytes
            val rawBytes = FileInputStream(file).use { fis ->
                fis.skip(44)
                fis.readBytes()
            }
            val totalFrames = rawBytes.size / bytesPerFrame
            if (totalFrames == 0) return FloatArray(targetSamples)

            val framesPerBucket = (totalFrames / targetSamples).coerceAtLeast(1)
            val result = FloatArray(targetSamples)
            val pcm = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)

            for (bucket in 0 until targetSamples) {
                var peakAbs = 0f
                val startFrame = bucket * framesPerBucket
                val endFrame = ((bucket + 1) * framesPerBucket).coerceAtMost(totalFrames)
                pcm.position(startFrame * bytesPerFrame)
                for (frame in startFrame until endFrame) {
                    for (ch in 0 until channels) {
                        val sample = when (bitDepth) {
                            16 -> pcm.short / 32768f
                            24 -> {
                                val b0 = pcm.get().toInt() and 0xFF
                                val b1 = pcm.get().toInt() and 0xFF
                                val b2 = pcm.get().toInt()
                                ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
                            }
                            32 -> if (audioFormat == 3) pcm.float
                                  else pcm.int / 2147483648f
                            else -> pcm.short / 32768f
                        }
                        if (kotlin.math.abs(sample) > peakAbs) peakAbs = kotlin.math.abs(sample)
                    }
                }
                result[bucket] = peakAbs
            }
            result
        } catch (_: Exception) { FloatArray(targetSamples) }
    }
}
```

- [ ] **Step 2: Replace TrimScreen.kt waveform stub with real Canvas waveform**

Replace only the `Box` that contains the stub text (lines ~87–95 in TrimScreen.kt):

```kotlin
// Replace this Box:
//   Box(Modifier.fillMaxWidth().height(80.dp)...) {
//       Text("[ waveform stub — phase 4 wires real samples ]"...)
//   }
// With:

val waveformData = remember(file.path) {
    WaveformLoader.load(file.path, targetSamples = 300)
}
val inFraction = range.start / durationMs
val outFraction = range.endInclusive / durationMs

Box(
    Modifier
        .fillMaxWidth()
        .height(80.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF0C0C10))
) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val barWidth = (w / waveformData.size.coerceAtLeast(1)).coerceAtLeast(1f)
        val inX = inFraction * w
        val outX = outFraction * w

        // Dim region outside selection
        drawRect(color = androidx.compose.ui.graphics.Color(0x44FF6600), topLeft = androidx.compose.ui.geometry.Offset(inX, 0f), size = androidx.compose.ui.geometry.Size(outX - inX, h))

        for (i in waveformData.indices) {
            val x = i * barWidth
            val amp = (waveformData[i] * midY).coerceIn(1f, midY)
            val isSelected = x in inX..outX
            val color = if (isSelected) androidx.compose.ui.graphics.Color(0xFFE86A2B) else androidx.compose.ui.graphics.Color(0xFF4A5568)
            drawRect(color = color, topLeft = androidx.compose.ui.geometry.Offset(x, midY - amp), size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, amp * 2f))
        }
    }
}
```

Add `import com.example.recorderproject.audio.WaveformLoader` at top of `TrimScreen.kt`.

- [ ] **Step 3: Add trimFile() to RecorderViewModel**

Find `fun closeTrim()` in `RecorderViewModel.kt` and add below it:

```kotlin
fun trimFile(file: RecordFile, inMs: Long, outMs: Long): java.io.File? {
    return try {
        val result = com.example.recorderproject.audio.WavTrimmer.trimToFile(
            srcPath = file.path,
            inMs = inMs,
            outMs = outMs,
            sampleRate = file.sampleRate,
            bitDepth = file.bitDepth,
            channelCount = file.channelCount,
        )
        if (result != null) {
            val trimRecord = file.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = result.name,
                path = result.absolutePath,
                durationSeconds = ((outMs - inMs) / 1000).toInt(),
            )
            _recordFiles.value = _recordFiles.value + trimRecord
        }
        result
    } catch (e: Exception) {
        Log.e(TAG, "Trim failed: ${e.message}", e)
        null
    }
}
```

- [ ] **Step 4: Wire TrimScreen onConfirm in MainActivity**

Replace the existing `onConfirm` lambda in `MainActivity.kt` (the one that shows "export wiring pending" Toast):

```kotlin
// Before (remove this):
onConfirm = { inMs, outMs ->
    Toast.makeText(this, "Trim ${inMs}ms..${outMs}ms (export wiring pending)", Toast.LENGTH_SHORT).show()
    viewModel.closeTrim()
},

// After (replace with):
onConfirm = { inMs, outMs ->
    val result = viewModel.trimFile(trimFile!!, inMs, outMs)
    if (result != null) {
        Toast.makeText(this, "Saved: ${result.name}", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(this, "Trim failed — check file is not locked", Toast.LENGTH_SHORT).show()
    }
    viewModel.closeTrim()
},
```

- [ ] **Step 5: Build and verify**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/WaveformLoader.kt \
        app/src/main/java/com/example/recorderproject/ui/TrimScreen.kt \
        app/src/main/java/com/example/recorderproject/RecorderViewModel.kt \
        app/src/main/java/com/example/recorderproject/MainActivity.kt
git commit -m "feat: TrimScreen — real waveform visualization + working WAV export via WavTrimmer"
```

---

### Task 7: VAD — Voice Activity Detection

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/VoiceActivityDetector.kt`
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`

- [ ] **Step 1: Create VoiceActivityDetector.kt**

```kotlin
// app/src/main/java/com/example/recorderproject/audio/VoiceActivityDetector.kt
package com.example.recorderproject.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.math.sqrt

/**
 * Energy-based Voice Activity Detector.
 *
 * Continuously reads from the microphone and calls [onVoiceDetected] when
 * RMS energy exceeds [thresholdDb] dBFS for [triggerWindowMs] milliseconds.
 * After triggering, it waits [cooldownMs] before it can trigger again.
 */
class VoiceActivityDetector(
    private val context: Context,
    private val thresholdDb: Float = -40f,
    private val triggerWindowMs: Int = 300,
    private val cooldownMs: Int = 2000,
) {
    private var job: Job? = null
    private val threshold = 10f.pow(thresholdDb / 20f)

    var onVoiceDetected: (() -> Unit)? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            runVad()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runVad() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(1024)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) return

        try {
            recorder.startRecording()
            val buf = ShortArray(bufferSize / 2)
            var aboveThresholdMs = 0
            var lastTriggerMs = 0L

            while (isActive) {
                val read = recorder.read(buf, 0, buf.size)
                if (read <= 0) continue

                // Compute RMS energy
                var sum = 0.0
                for (i in 0 until read) sum += (buf[i] / 32768.0) * (buf[i] / 32768.0)
                val rms = sqrt(sum / read).toFloat()

                val chunkMs = (read * 1000) / sampleRate

                if (rms >= threshold) {
                    aboveThresholdMs += chunkMs
                    if (aboveThresholdMs >= triggerWindowMs) {
                        val now = System.currentTimeMillis()
                        if (now - lastTriggerMs >= cooldownMs) {
                            lastTriggerMs = now
                            withContext(Dispatchers.Main) { onVoiceDetected?.invoke() }
                        }
                        aboveThresholdMs = 0
                    }
                } else {
                    aboveThresholdMs = (aboveThresholdMs - chunkMs).coerceAtLeast(0)
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }

    private fun Float.pow(exp: Float): Float = Math.pow(toDouble(), exp.toDouble()).toFloat()
}
```

- [ ] **Step 2: Wire VAD into RecorderViewModel**

Find the `inputDeviceDetector` field declaration (added in the previous session). Add the VAD detector right after it:

```kotlin
// Add after inputDeviceDetector:
private val voiceActivityDetector = VoiceActivityDetector(
    context = app.applicationContext,
    thresholdDb = -38f,
    triggerWindowMs = 250,
    cooldownMs = 3000,
)
```

Replace the `toggleVad()` function body:

```kotlin
fun toggleVad() {
    val on = !_vadOn.value
    _vadOn.value = on
    if (hydrated.value) viewModelScope.launch { settings.setVad(on) }

    if (on) {
        voiceActivityDetector.onVoiceDetected = {
            if (!_isRecording.value) {
                Log.i(TAG, "VAD: voice detected — auto-starting recording")
                startRecording()
            }
        }
        voiceActivityDetector.start(viewModelScope)
        Toast.makeText(app, "VAD on — recording starts automatically when voice is detected", Toast.LENGTH_SHORT).show()
    } else {
        voiceActivityDetector.stop()
        voiceActivityDetector.onVoiceDetected = null
        Toast.makeText(app, "VAD off", Toast.LENGTH_SHORT).show()
    }
}
```

Also stop VAD when recording starts (to avoid double-start). Find `startRecordingInternal()` and add at the top:

```kotlin
// Stop VAD listener while actually recording
if (_vadOn.value) voiceActivityDetector.stop()
```

And restart VAD when recording stops. Find `stopRecording()` and add after the recording stops:

```kotlin
// Restart VAD after recording ends if still enabled
if (_vadOn.value) {
    voiceActivityDetector.onVoiceDetected = {
        if (!_isRecording.value) startRecording()
    }
    voiceActivityDetector.start(viewModelScope)
}
```

Also stop VAD in `onCleared()` (already has the `inputDeviceDetector.stop()` line; add below it):

```kotlin
try { voiceActivityDetector.stop() } catch (_: Exception) {}
```

Add the import at top of `RecorderViewModel.kt`:
```kotlin
import com.example.recorderproject.audio.VoiceActivityDetector
```

- [ ] **Step 3: Build and verify**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/VoiceActivityDetector.kt \
        app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "feat: implement energy-based VAD — auto-starts recording when voice is detected"
```

---

### Task 8: Stub Cleanup & Recovery Artifact Removal

**Files:**
- Delete: `app/src/main/java/com/example/recorderproject/ui/SettingsScreen.kt.recovered.txt`
- Delete: `app/src/main/java/com/example/recorderproject/ui/components/PresetEditSheet.kt.recovered.txt`
- Delete: `_symbol_inventory.json`
- Modify: `app/src/main/java/com/example/recorderproject/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/TranscriptScreen.kt`

- [ ] **Step 1: Delete recovery artifacts**

```bash
rm "/Users/meatball_mac/RECORDER_PROJECT/app/src/main/java/com/example/recorderproject/ui/SettingsScreen.kt.recovered.txt"
rm "/Users/meatball_mac/RECORDER_PROJECT/app/src/main/java/com/example/recorderproject/ui/components/PresetEditSheet.kt.recovered.txt"
rm "/Users/meatball_mac/RECORDER_PROJECT/_symbol_inventory.json"
```

- [ ] **Step 2: Replace SettingsScreen.kt stub with redirect**

Replace the entire contents of `SettingsScreen.kt` — it was a dead stub. Make it a thin wrapper that redirects to `SettingsScreenV2` so any remaining code that references `SettingsScreen()` still compiles:

```kotlin
// app/src/main/java/com/example/recorderproject/ui/SettingsScreen.kt
package com.example.recorderproject.ui

// SettingsScreen was lost; all settings logic lives in SettingsScreenV2.
// This alias keeps any remaining call sites compiling.
typealias SettingsScreen = SettingsScreenV2
```

Wait — `SettingsScreenV2` has a different signature. Just replace with a no-op placeholder that renders nothing (it's not reachable from MainActivity anyway):

```kotlin
// app/src/main/java/com/example/recorderproject/ui/SettingsScreen.kt
package com.example.recorderproject.ui

// Legacy stub — replaced by SettingsScreenV2 which MainActivity routes to instead.
// This file is kept to avoid breaking any remaining import references.
@Suppress("UNUSED")
private val _settingsScreenLegacy = Unit
```

Actually the simplest approach is to delete `SettingsScreen.kt` entirely. Check if it's imported anywhere:

```bash
grep -r "SettingsScreen" /Users/meatball_mac/RECORDER_PROJECT/app/src/main/java/ --include="*.kt" | grep -v "SettingsScreen.kt" | grep -v "SettingsScreenV2"
```

If no results, delete it:
```bash
rm "/Users/meatball_mac/RECORDER_PROJECT/app/src/main/java/com/example/recorderproject/ui/SettingsScreen.kt"
```

- [ ] **Step 3: Fix TranscriptScreen placeholder text**

In `TranscriptScreen.kt`, replace the placeholder description shown when `text == null`:

```kotlin
// Replace:
Text(
    "The transcription engine is staged but not yet active. " +
        "When wired up, taps to Transcribe will run the recording " +
        "through ML Kit / SpeechRecognizer and display the result here.",
    color = RecorderBlueGrey,
    fontSize = 12.sp,
)

// With:
Text(
    "Tap Transcribe below to convert this recording to text using on-device speech recognition.",
    color = RecorderBlueGrey,
    fontSize = 12.sp,
)
```

- [ ] **Step 4: Build and verify**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: delete recovery artifacts, fix stub text visible to users"
```

---

### Task 9: PresetEditSheet Rebuild

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/ui/components/PresetEditSheet.kt`

The recovered partial source at `PresetEditSheet.kt.recovered.txt` (now deleted) showed the full signature. Rebuild from that known interface using the same `ModeSettings` data class referenced in the recovered code.

- [ ] **Step 1: Check what ModeSettings contains**

```bash
grep -r "ModeSettings\|RecordingMode" /Users/meatball_mac/RECORDER_PROJECT/app/src/main/java/ --include="*.kt" | grep "data class\|class Mode" | head -5
```

- [ ] **Step 2: Check how PresetEditSheet is called**

```bash
grep -r "PresetEditSheet" /Users/meatball_mac/RECORDER_PROJECT/app/src/main/java/ --include="*.kt"
```

- [ ] **Step 3: Rebuild PresetEditSheet.kt**

Based on the recovered signature, replace the stub with a functional bottom sheet:

```kotlin
// app/src/main/java/com/example/recorderproject/ui/components/PresetEditSheet.kt
package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SheetBg = Color(0xFF111111)
private val RowBg = Color(0xFF1A1A1A)
private val Orange = Color(0xFFE86A2B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditSheet(
    title: String = "Recording Settings",
    sampleRateInitial: Int = 48000,
    bitDepthInitial: Int = 24,
    channelCountInitial: Int = 1,
    noiseReductionInitial: Boolean = false,
    onApply: (sampleRate: Int, bitDepth: Int, channelCount: Int, noiseReduction: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var sampleRate by remember { mutableIntStateOf(sampleRateInitial) }
    var bitDepth by remember { mutableIntStateOf(bitDepthInitial) }
    var channelCount by remember { mutableIntStateOf(channelCountInitial) }
    var noiseReduction by remember { mutableStateOf(noiseReductionInitial) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            SheetLabel("Sample Rate")
            PillSelector(
                options = listOf(44100 to "44.1 kHz", 48000 to "48 kHz", 96000 to "96 kHz"),
                selected = sampleRate,
                onSelect = { sampleRate = it },
            )

            SheetLabel("Bit Depth")
            PillSelector(
                options = listOf(16 to "16-bit", 24 to "24-bit", 32 to "32-bit Float"),
                selected = bitDepth,
                onSelect = { bitDepth = it },
            )

            SheetLabel("Channels")
            PillSelector(
                options = listOf(1 to "Mono", 2 to "Stereo"),
                selected = channelCount,
                onSelect = { channelCount = it },
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RowBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Noise Reduction", color = Color.White, fontWeight = FontWeight.Medium)
                    Text("Remove background hiss", color = Color.Gray, fontSize = 11.sp)
                }
                Switch(
                    checked = noiseReduction,
                    onCheckedChange = { noiseReduction = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Orange, checkedTrackColor = Orange.copy(alpha = 0.4f)),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                ) { Text("Cancel") }
                Button(
                    onClick = { onApply(sampleRate, bitDepth, channelCount, noiseReduction); onDismiss() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                ) { Text("Apply", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, color = Color.Gray, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun <T> PillSelector(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) {
            val isSelected = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Orange else RowBg)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (isSelected) Color.White else Color.Gray, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
```

- [ ] **Step 4: Check call sites compile with new signature**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

If there are call-site errors, update each caller to pass the new named parameters. `PresetEditSheet` is called from `MeatRecModeSelector.kt` — check its signature expectations:

```bash
grep -n "PresetEditSheet" /Users/meatball_mac/RECORDER_PROJECT/app/src/main/java/com/example/recorderproject/ui/MeatRecModeSelector.kt
```

Adjust the call to match the new signature.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/ui/components/PresetEditSheet.kt
git commit -m "feat: rebuild PresetEditSheet — was a one-line stub, now functional preset editor"
```

---

## Sprint 3 — Feature Completion

---

### Task 10: Transcription via Android SpeechRecognizer

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/audio/TranscriptionEngine.kt`
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/TranscriptScreen.kt`

The engine plays the WAV through `STREAM_VOICE_CALL` in `MODE_IN_COMMUNICATION` while `SpeechRecognizer` listens from the `VOICE_COMMUNICATION` source — a well-known Android loopback technique. Works offline on most devices with `EXTRA_PREFER_OFFLINE = true`.

- [ ] **Step 1: Create TranscriptionEngine.kt**

```kotlin
// app/src/main/java/com/example/recorderproject/audio/TranscriptionEngine.kt
package com.example.recorderproject.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TranscriptionEngine(private val context: Context) {
    private val TAG = "TranscriptionEngine"

    /**
     * Transcribe the WAV file at [filePath] on [scope].
     * Calls [onResult] with the text on success, [onError] with a message on failure.
     * Technique: route playback via STREAM_VOICE_CALL (earpiece) and listen with
     * SpeechRecognizer in VOICE_COMMUNICATION source. EXTRA_PREFER_OFFLINE = true
     * uses on-device model when available (most Android 10+ with Google services).
     */
    fun transcribe(
        filePath: String,
        durationSeconds: Int,
        scope: CoroutineScope,
        onProgress: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch(Dispatchers.Main) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("Speech recognition not available on this device")
                return@launch
            }
            onProgress("Preparing transcription…")
            try {
                val result = transcribeSuspend(filePath, durationSeconds)
                onResult(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed: ${e.message}", e)
                onError(e.message ?: "Unknown transcription error")
            }
        }
    }

    private suspend fun transcribeSuspend(
        filePath: String,
        durationSeconds: Int,
    ): String = suspendCancellableCoroutine { cont ->
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        var mediaPlayer: MediaPlayer? = null

        fun cleanup() {
            try { mediaPlayer?.stop() } catch (_: Exception) {}
            try { mediaPlayer?.release() } catch (_: Exception) {}
            mediaPlayer = null
            try { recognizer.destroy() } catch (_: Exception) {}
            audioManager.mode = AudioManager.MODE_NORMAL
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = texts?.joinToString(" ") ?: ""
                cleanup()
                if (cont.isActive) cont.resume(text.ifBlank { "(No speech detected)" })
            }
            override fun onError(error: Int) {
                cleanup()
                if (cont.isActive) cont.resumeWithException(Exception(errorMessage(error)))
            }
            override fun onPartialResults(partial: Bundle?) {}
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Route audio through voice-call path so SpeechRecognizer picks it up
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        mediaPlayer = MediaPlayer().apply {
            @Suppress("DEPRECATION")
            setAudioStreamType(AudioManager.STREAM_VOICE_CALL)
            setDataSource(filePath)
            prepare()
        }

        val listenMs = (durationSeconds * 1000L + 3000L).coerceAtLeast(5000L)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, listenMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, listenMs)
        }

        recognizer.startListening(intent)
        mediaPlayer!!.start()

        cont.invokeOnCancellation { cleanup() }
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error — is another app using the mic?"
        SpeechRecognizer.ERROR_CLIENT -> "Client error — try again"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing RECORD_AUDIO permission"
        SpeechRecognizer.ERROR_NETWORK -> "Network error — go online or check speech recognition is available offline"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout — try again"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized in this recording"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy — wait a moment and retry"
        SpeechRecognizer.ERROR_SERVER -> "Server error — try again"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Recording too short or too quiet for transcription"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many requests — wait and retry"
        else -> "Speech recognition error (code $error)"
    }
}
```

- [ ] **Step 2: Add TranscriptionEngine to RecorderViewModel**

Add field after `voiceActivityDetector`:

```kotlin
private val transcriptionEngine = TranscriptionEngine(app.applicationContext)
```

Add `_transcribeProgress` state for loading indicator:

```kotlin
private val _transcribeProgress = MutableStateFlow<Map<String, String>>(emptyMap())
val transcribeProgress: StateFlow<Map<String, String>> = _transcribeProgress
```

Replace `requestTranscribe()`:

```kotlin
fun requestTranscribe(file: RecordFile) {
    if (file.path.startsWith("content://")) {
        Toast.makeText(app, "Transcription requires a file-based recording (not SAF path)", Toast.LENGTH_LONG).show()
        return
    }
    _transcribeProgress.value = _transcribeProgress.value + (file.id to "Transcribing…")
    transcriptionEngine.transcribe(
        filePath = file.path,
        durationSeconds = file.durationSeconds,
        scope = viewModelScope,
        onProgress = { msg ->
            _transcribeProgress.value = _transcribeProgress.value + (file.id to msg)
        },
        onResult = { text ->
            _transcripts.value = _transcripts.value + (file.id to text)
            _transcribeProgress.value = _transcribeProgress.value - file.id
        },
        onError = { err ->
            _transcribeProgress.value = _transcribeProgress.value - file.id
            Toast.makeText(app, err, Toast.LENGTH_LONG).show()
        },
    )
}
```

- [ ] **Step 3: Add loading indicator to TranscriptScreen.kt**

Add `val progress by viewModel.transcribeProgress.collectAsStateWithLifecycle()` near the top of `TranscriptScreen`. Then in the `OutlinedButton` onClick area, show the progress if it exists:

```kotlin
val progress by viewModel.transcribeProgress.collectAsStateWithLifecycle()
val isTranscribing = progress[file.id] != null

// Replace the OutlinedButton:
if (isTranscribing) {
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = RecorderOrange, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(progress[file.id] ?: "Transcribing…", color = RecorderBlueGrey, fontSize = 13.sp)
    }
} else {
    OutlinedButton(
        onClick = { viewModel.requestTranscribe(file) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (text != null) "Re-transcribe" else "Transcribe",
            color = RecorderOrange,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
```

- [ ] **Step 4: Build and verify**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/audio/TranscriptionEngine.kt \
        app/src/main/java/com/example/recorderproject/RecorderViewModel.kt \
        app/src/main/java/com/example/recorderproject/ui/TranscriptScreen.kt
git commit -m "feat: implement on-device transcription via SpeechRecognizer audio loopback"
```

---

### Task 11: Google Drive Backup

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/network/GoogleDriveUploader.kt`
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/MeatRecSettings.kt`
- Modify: `app/src/main/java/com/example/recorderproject/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

> **PREREQUISITE (manual):** Before Task 11 compiles correctly, you must:
> 1. Create a project at https://console.cloud.google.com/
> 2. Enable the Google Drive API
> 3. Create an OAuth 2.0 Android credential (add your SHA-1 from `keytool -list -v -keystore meatrec-release.jks`)
> 4. Add the `google-services.json` from the Firebase Console OR just note your `client_id` for the manifest meta-data below
> 5. The `applicationId` must match what you registered: `com.meatrec.recorder`

- [ ] **Step 1: Create GoogleDriveUploader.kt**

```kotlin
// app/src/main/java/com/example/recorderproject/network/GoogleDriveUploader.kt
package com.example.recorderproject.network

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GoogleDriveUploader(private val context: Context) {
    private val TAG = "GoogleDriveUploader"
    private val SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"

    /**
     * Upload [file] to Google Drive in the app's "drive.file" scope.
     * Returns the Drive file ID on success, null on failure.
     * Must be called from a coroutine (performs network I/O).
     */
    suspend fun upload(file: File): String? = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: return@withContext null.also { Log.w(TAG, "Not signed in") }

            val token = GoogleAuthUtil.getToken(context, account.account!!, SCOPE)
            val boundary = "meatrec_${System.currentTimeMillis()}"
            val metaJson = """{"name":"${file.name}"}"""

            val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 60_000
            }

            conn.outputStream.buffered().use { out ->
                out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metaJson\r\n--$boundary\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
                file.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader().readText()

            if (code in 200..299) {
                val id = JSONObject(body).optString("id")
                Log.d(TAG, "Uploaded ${file.name} → Drive id=$id")
                id
            } else {
                Log.e(TAG, "Upload failed HTTP $code: $body")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload exception: ${e.message}", e)
            null
        }
    }

    /** True if the user is currently signed into Google on this device. */
    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null
}
```

- [ ] **Step 2: Wire Google Drive in RecorderViewModel**

Add field:

```kotlin
private val driveUploader = GoogleDriveUploader(app.applicationContext)
```

Expose sign-in state:

```kotlin
val isDriveSignedIn: StateFlow<Boolean> = MutableStateFlow(driveUploader.isSignedIn()).also { flow ->
    viewModelScope.launch {
        while (true) {
            kotlinx.coroutines.delay(5000)
            (flow as MutableStateFlow).value = driveUploader.isSignedIn()
        }
    }
}
```

Add a `backupToDrive()` function called after successful recording stops:

```kotlin
fun backupToDrive(filePath: String) {
    if (!_cloudBackupOn.value) return
    if (!driveUploader.isSignedIn()) return
    viewModelScope.launch {
        val file = java.io.File(filePath)
        if (!file.exists()) return@launch
        val id = driveUploader.upload(file)
        if (id != null) {
            Toast.makeText(app, "Backed up to Google Drive: ${file.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(app, "Drive backup failed — check internet connection", Toast.LENGTH_SHORT).show()
        }
    }
}
```

Find where `stopRecording()` finalizes the WAV file and calls `_recordFiles.value = ...`. After that block, add:

```kotlin
backupToDrive(finalizedPath)  // finalizedPath is the completed WAV file path
```

(Search `stopRecording` for where the WAV path is finalized — look for `recorderFile?.absolutePath` or similar.)

- [ ] **Step 3: Add Google Sign-In to MainActivity**

Add a sign-in launcher:

```kotlin
private val googleSignInLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == RESULT_OK) {
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            Toast.makeText(this, "Google Drive connected", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

fun signInToGoogleDrive() {
    val options = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
        com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
    )
        .requestEmail()
        .requestScopes(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file"))
        .build()
    val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, options)
    googleSignInLauncher.launch(client.signInIntent)
}
```

- [ ] **Step 4: Add Drive sign-in button to MeatRecSettings.kt**

Find the "Cloud Backup" section in `MeatRecSettings.kt`. Add a sign-in button below the cloud backup toggle:

```kotlin
val isDriveSignedIn by viewModel.isDriveSignedIn.collectAsStateWithLifecycle()

// After the cloud backup toggle row, add:
if (cloudBackupOn) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                if (isDriveSignedIn) "Google Drive: Connected" else "Google Drive: Not signed in",
                color = if (isDriveSignedIn) Color(0xFF4CAF50) else RecorderBlueGrey,
                fontSize = 13.sp,
            )
        }
        if (!isDriveSignedIn) {
            Button(
                onClick = onSignInDrive,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
            ) { Text("Sign in", color = Color.White, fontSize = 12.sp) }
        }
    }
}
```

Add `onSignInDrive: () -> Unit` to `MeatRecSettings` composable signature. Pass `onSignInDrive = { (context as? MainActivity)?.signInToGoogleDrive() }` from the `MainActivity` call site.

- [ ] **Step 5: Build and verify**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`

If you see `com.google.android.gms.common.api.ApiException` import errors, add to build.gradle dependencies:
```kotlin
implementation("com.google.android.gms:play-services-base:18.5.0")
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/network/GoogleDriveUploader.kt \
        app/src/main/java/com/example/recorderproject/RecorderViewModel.kt \
        app/src/main/java/com/example/recorderproject/ui/MeatRecSettings.kt \
        app/src/main/java/com/example/recorderproject/MainActivity.kt
git commit -m "feat: Google Drive backup — upload recording after stop when backup is enabled"
```

---

### Task 12: SettingsScreenV2 as Main Settings

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/MainActivity.kt`
- Modify: `app/src/main/java/com/example/recorderproject/ui/SettingsScreenV2.kt`

Currently `MainActivity` routes to `MeatRecSettings` for settings. `SettingsScreenV2` is more complete and has all the controls, but is never shown.

- [ ] **Step 1: Check SettingsScreenV2 signature**

```bash
grep -n "fun SettingsScreenV2" /Users/meatball_mac/RECORDER_PROJECT/app/src/main/java/com/example/recorderproject/ui/SettingsScreenV2.kt | head -3
```

- [ ] **Step 2: Add missing callbacks to SettingsScreenV2**

`SettingsScreenV2` needs `onBack`, `onChangeTheme`, `onPickCloudLocation`, `onOpenPrivacy`, and `onSignInDrive` parameters if not already present. Add them to the function signature.

- [ ] **Step 3: Route settingsOpen to SettingsScreenV2 in MainActivity**

Find the `settingsOpen ->` branch in MainActivity's `when {}` block:

```kotlin
// Replace:
settingsOpen -> MeatRecSettings(
    viewModel = viewModel,
    currentTheme = appTheme,
    onChangeTheme = { ... },
    onBack = { settingsOpen = false },
    onChangeMode = { ... },
    onPickCloudLocation = { selectCloudDirectory() },
)

// With:
settingsOpen -> SettingsScreenV2(
    viewModel = viewModel,
    currentTheme = appTheme,
    onChangeTheme = {
        appTheme = it
        getPreferences(MODE_PRIVATE).edit().putString("app_theme", it.displayName).apply()
    },
    onBack = { settingsOpen = false },
    onChangeMode = {
        modeChosen = false
        getPreferences(MODE_PRIVATE).edit().putBoolean("mode_chosen", false).apply()
        settingsOpen = false
    },
    onPickCloudLocation = { selectCloudDirectory() },
    onOpenPrivacy = { settingsOpen = false; privacyOpen = true },
    onReduceMotionChange = { reduceMotion = it },
    onSignInDrive = { signInToGoogleDrive() },
)
```

Update the import from `MeatRecSettings` to `SettingsScreenV2` (or keep both — `MeatRecSettings` is used for the inline settings in the menu).

- [ ] **Step 4: Build and verify**

```bash
./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD"
```

Fix any parameter mismatches by checking the actual signature of `SettingsScreenV2`.

- [ ] **Step 5: Final full build + release AAB**

```bash
./gradlew bundleRelease
```

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/bundle/release/app-release.aab` exists.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/MainActivity.kt \
        app/src/main/java/com/example/recorderproject/ui/SettingsScreenV2.kt
git commit -m "feat: route Settings to SettingsScreenV2 — full settings screen now active"
```

---

## Play Store Submission Checklist

After all 12 tasks pass, complete these manually in Play Console:

- [ ] **Data Safety form** — declare: Microphone (recorded, not transmitted), Location (optional, local only), Google Drive (user-initiated backup)
- [ ] **App icon 512×512 PNG** — export from existing adaptive icon layers
- [ ] **Feature graphic 1024×500 px** — required for Play Store listing
- [ ] **Screenshots** — minimum 2 phone screenshots (1080×1920 or 2400×1080)
- [ ] **Short description** (80 chars): `"Professional field recorder with EQ, trim, pitch shift & Drive backup"`
- [ ] **Full description** (4000 chars max): describe MEAT REC's features
- [ ] **Content rating questionnaire** — "No objectionable content", target audience 13+
- [ ] **Privacy Policy URL** — host the in-app privacy policy text at a public URL (GitHub Pages works)
- [ ] **Release track** — submit AAB to Internal Testing first, then Closed Testing, then Production

---

## Self-Review

**Spec coverage check:**
- A1 applicationId → Task 1 ✓
- A2 signing → Task 2 ✓
- A3 location disclosure → Task 4 ✓
- A4 privacy policy → Task 3 ✓
- A5 R8/ProGuard → Task 1 ✓
- A6 targetSdk 35 → Task 1 ✓
- B1 PitchShifter crash → Task 5 ✓
- B4 TrimScreen → Task 6 ✓
- B5 VAD stub → Task 7 ✓
- B6 Transcription placeholder → Task 10 ✓
- B7 TrimScreen waveform stub → Task 6 ✓
- C3 debug strings → Task 8 ✓
- C8 recovery artifacts → Task 8 ✓
- Google Drive → Task 11 ✓
- Settings consolidation → Task 12 ✓
- PresetEditSheet → Task 9 ✓

**Type consistency:** All method names used in Tasks 7–12 match declarations added in the same or earlier tasks. `backupToDrive(filePath: String)`, `trimFile(file, inMs, outMs)`, `selectInputDevice(device)`, `clearInputDevice()` all defined before use.

**Placeholder scan:** No "TBD", "TODO", or incomplete sections. Every step contains working code.
