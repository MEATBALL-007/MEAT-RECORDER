# RecordingScanner + RecordingNaming Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move library-scanning logic out of `RecorderViewModel` into a `RecordingScanner` collaborator backed by a pure, unit-tested `RecordingNaming` rules object, with the ViewModel still owning `_recordFiles`.

**Architecture:** Task 1 TDDs the pure `RecordingNaming` rules. Task 2 adds `RecordingScanner` (Android/I/O collaborator that delegates decisions to `RecordingNaming` and returns `List<RecordFile>`); it compiles standalone, unused. Task 3 rewires the ViewModel's two private scan functions to delegate and adds the `scanner` field. The scanner is Android-coupled, so its regression guard is clean compile + the full existing unit suite staying green; the pure rules get real tests.

**Tech Stack:** Kotlin, Android (`Context`, `DocumentFile`, `MediaMetadataRetriever`, `ContentResolver`), JUnit4.

**Spec:** `docs/superpowers/specs/2026-06-08-recordingscanner-extraction-design.md`

---

## File Structure

- **Create** `app/src/main/java/com/example/recorderproject/data/RecordingNaming.kt` — pure naming/filtering rules. No Android types. One responsibility; unit-tested.
- **Create** `app/src/test/java/com/example/recorderproject/data/RecordingNamingTest.kt` — JVM tests for the rules.
- **Create** `app/src/main/java/com/example/recorderproject/data/RecordingScanner.kt` — Android collaborator doing File/DocumentFile/MMR/ContentResolver I/O, delegating to `RecordingNaming`, returning `List<RecordFile>`.
- **Modify** `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` — add `scanner` field (right after `settings`, line ~148, so it is initialized before the `init` block at line ~154 that calls the scans); replace the bodies of `scanRecordingsFromDisk()` (2787-2876) and `scanSafRecordings()` (2884-2956) with delegation. No import changes (scan bodies used fully-qualified Android names).

---

## Task 1: RecordingNaming (pure rules, TDD)

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/data/RecordingNaming.kt`
- Test: `app/src/test/java/com/example/recorderproject/data/RecordingNamingTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/example/recorderproject/data/RecordingNamingTest.kt`:

```kotlin
package com.example.recorderproject.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingNamingTest {

    @Test fun `baseOf strips the extension`() {
        assertEquals("REC_001", RecordingNaming.baseOf("REC_001.wav"))
        assertEquals("REC_001", RecordingNaming.baseOf("REC_001"))
    }

    @Test fun `hasNr detects the noise-reduction suffix case-insensitively`() {
        assertTrue(RecordingNaming.hasNr("take1_nr"))
        assertTrue(RecordingNaming.hasNr("take1_NR"))
        assertFalse(RecordingNaming.hasNr("take1"))
    }

    @Test fun `isDeliverySibling detects the delivery suffix`() {
        assertTrue(RecordingNaming.isDeliverySibling("take1_delivery"))
        assertFalse(RecordingNaming.isDeliverySibling("take1"))
    }

    @Test fun `nrShadowedBases lists originals that have an nr twin`() {
        assertEquals(setOf("take1"), RecordingNaming.nrShadowedBases(listOf("take1", "take1_nr", "take2")))
    }

    @Test fun `nrShadowedBases is empty when no nr twins exist`() {
        assertEquals(emptySet<String>(), RecordingNaming.nrShadowedBases(listOf("take1", "take2")))
    }

    @Test fun `isHidden hides delivery siblings`() {
        assertTrue(RecordingNaming.isHidden("take1_delivery", emptySet()))
    }

    @Test fun `isHidden hides an original shadowed by its nr twin`() {
        assertTrue(RecordingNaming.isHidden("take1", setOf("take1")))
    }

    @Test fun `isHidden keeps the nr file itself and unrelated files`() {
        assertFalse(RecordingNaming.isHidden("take1_nr", setOf("take1")))
        assertFalse(RecordingNaming.isHidden("take2", setOf("take1")))
    }

    @Test fun `sidecar name builders match the on-disk convention`() {
        assertEquals("take1_eq.json", RecordingNaming.eqSidecarName("take1"))
        assertEquals("take1_delivery.wav", RecordingNaming.deliveryWavName("take1"))
        assertEquals("take1_delivery.json", RecordingNaming.deliveryJsonName("take1"))
    }

    @Test fun `isOrphanDelivery is true only for a delivery with no original and no sidecar`() {
        assertTrue(RecordingNaming.isOrphanDelivery("take1_delivery", originalBases = emptySet(), hasSidecarJson = false))
        assertFalse(RecordingNaming.isOrphanDelivery("take1_delivery", originalBases = setOf("take1"), hasSidecarJson = false))
        assertFalse(RecordingNaming.isOrphanDelivery("take1_delivery", originalBases = emptySet(), hasSidecarJson = true))
        assertFalse(RecordingNaming.isOrphanDelivery("take1", originalBases = emptySet(), hasSidecarJson = false))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.data.RecordingNamingTest"`
Expected: FAIL — `Unresolved reference 'RecordingNaming'`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/example/recorderproject/data/RecordingNaming.kt`:

```kotlin
package com.example.recorderproject.data

/**
 * Pure naming/filtering rules for the recording library (the `_nr` noise-reduction twin,
 * `_delivery` siblings, `_eq.json`/`_delivery.json` sidecars, orphan-delivery cleanup).
 * No Android types, so it is fully unit-testable. Used by RecordingScanner.
 */
object RecordingNaming {
    private const val NR_SUFFIX = "_nr"
    private const val DELIVERY_SUFFIX = "_delivery"

    /** Name without its extension. */
    fun baseOf(name: String): String =
        if (name.contains('.')) name.substringBeforeLast('.') else name

    /** A noise-reduced file (base ends with `_nr`, case-insensitive). */
    fun hasNr(base: String): Boolean = base.lowercase().endsWith(NR_SUFFIX)

    /** A delivery render sibling (base ends with `_delivery`). */
    fun isDeliverySibling(base: String): Boolean = base.endsWith(DELIVERY_SUFFIX)

    /** Original base names that have an `_nr` twin — these originals are hidden in favor of the twin. */
    fun nrShadowedBases(wavBaseNames: Collection<String>): Set<String> =
        wavBaseNames.filter { it.lowercase().endsWith(NR_SUFFIX) }
            .map { it.dropLast(NR_SUFFIX.length) }
            .toSet()

    /** Whether a wav (by base name) should be hidden from the library list. */
    fun isHidden(base: String, nrShadowed: Set<String>): Boolean =
        isDeliverySibling(base) || (!hasNr(base) && nrShadowed.contains(base))

    fun eqSidecarName(base: String): String = "${base}_eq.json"
    fun deliveryWavName(base: String): String = "${base}_delivery.wav"
    fun deliveryJsonName(base: String): String = "${base}_delivery.json"

    /** A `_delivery.wav` whose original is gone and which has no sidecar JSON — safe to delete. */
    fun isOrphanDelivery(base: String, originalBases: Set<String>, hasSidecarJson: Boolean): Boolean {
        if (!isDeliverySibling(base)) return false
        val originalBase = base.removeSuffix(DELIVERY_SUFFIX)
        return originalBase !in originalBases && !hasSidecarJson
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.recorderproject.data.RecordingNamingTest"`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/data/RecordingNaming.kt \
        app/src/test/java/com/example/recorderproject/data/RecordingNamingTest.kt
git commit -m "feat: RecordingNaming pure rules for library scanning (#13 step 2)"
```

---

## Task 2: RecordingScanner (Android collaborator, standalone)

**Files:**
- Create: `app/src/main/java/com/example/recorderproject/data/RecordingScanner.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.example.recorderproject.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.recorderproject.model.DeliveryResult
import com.example.recorderproject.model.RecordFile
import java.io.File
import java.util.UUID

/**
 * Reconstructs the recording library from internal app storage and/or a SAF folder, returning
 * List<RecordFile>; the caller owns the list. Android/I/O-coupled — all naming/filtering
 * decisions delegate to the pure, unit-tested [RecordingNaming].
 */
class RecordingScanner(private val app: Context) {

    /** Scan internal app storage. Also sweeps orphaned `_delivery.wav` files. */
    fun scanDisk(existingPaths: Set<String>): List<RecordFile> {
        val dir = File(app.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Recordings")
        if (!dir.exists()) return emptyList()
        val allWavs = dir.listFiles { f -> f.isFile && f.extension.equals("wav", ignoreCase = true) }
            ?.toList() ?: return emptyList()

        val nrShadowed = RecordingNaming.nrShadowedBases(allWavs.map { it.nameWithoutExtension })

        val scanned = allWavs
            .filter { f ->
                val base = f.nameWithoutExtension
                !RecordingNaming.isHidden(base, nrShadowed) && f.absolutePath !in existingPaths
            }
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f ->
                try {
                    val base = f.nameWithoutExtension
                    val hasNr = RecordingNaming.hasNr(base)
                    val hasEq = File(f.parentFile, RecordingNaming.eqSidecarName(base)).exists()

                    val durationSeconds = try {
                        val mmr = MediaMetadataRetriever()
                        mmr.setDataSource(f.absolutePath)
                        val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        mmr.release()
                        (ms / 1000L).toInt()
                    } catch (_: Exception) { 0 }

                    val deliveryWav = File(f.parentFile, RecordingNaming.deliveryWavName(base))
                    val deliveryJson = File(f.parentFile, RecordingNaming.deliveryJsonName(base))
                    val deliveryResult = runCatching {
                        if (deliveryJson.exists()) DeliveryResult.fromJson(deliveryJson.readText()) else null
                    }.getOrNull()
                    val deliveryPath = if (deliveryWav.exists()) deliveryWav.absolutePath else null

                    RecordFile(
                        id = UUID.randomUUID().toString(),
                        name = f.name,
                        path = f.absolutePath,
                        durationSeconds = durationSeconds,
                        sceneName = "",
                        hasNoiseReduction = hasNr,
                        hasEQ = hasEq,
                        deliveryPath = deliveryPath,
                        deliveryResult = deliveryResult,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "scanDisk: skipping ${f.name}: ${e.message}")
                    null
                }
            }

        // Orphan delivery sweep: `_delivery.wav` with no original and no sidecar JSON.
        val originalBases = allWavs
            .filter { !RecordingNaming.isDeliverySibling(it.nameWithoutExtension) }
            .map { it.nameWithoutExtension }
            .toSet()
        allWavs
            .filter { f ->
                val base = f.nameWithoutExtension
                val sidecar = File(f.parentFile, "${base}.json")
                RecordingNaming.isOrphanDelivery(base, originalBases, sidecar.exists())
            }
            .forEach { runCatching { it.delete() } }

        return scanned
    }

    /** Scan a SAF (folder-picker) save location. Best-effort and fully guarded; never throws. */
    fun scanSaf(treeUri: Uri, existingPaths: Set<String>): List<RecordFile> {
        val tree = try { DocumentFile.fromTreeUri(app, treeUri) } catch (_: Exception) { null } ?: return emptyList()
        val docs = try { tree.listFiles().toList() } catch (_: Exception) { return emptyList() }

        // name -> doc, for sidecar/companion (_eq.json, _delivery.*) lookups
        val byName = docs.mapNotNull { d -> d.name?.let { it to d } }.toMap()
        val wavs = docs.filter { it.isFile && (it.name?.endsWith(".wav", ignoreCase = true) == true) }
        val nrShadowed = RecordingNaming.nrShadowedBases(wavs.mapNotNull { it.name }.map { RecordingNaming.baseOf(it) })

        return wavs
            .sortedByDescending { it.lastModified() }
            .mapNotNull { doc ->
                try {
                    val fullName = doc.name ?: return@mapNotNull null
                    val base = RecordingNaming.baseOf(fullName)
                    if (RecordingNaming.isHidden(base, nrShadowed)) return@mapNotNull null
                    val path = doc.uri.toString()
                    if (path in existingPaths) return@mapNotNull null

                    val durationSeconds = try {
                        val mmr = MediaMetadataRetriever()
                        mmr.setDataSource(app, doc.uri)
                        val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        mmr.release()
                        (ms / 1000L).toInt()
                    } catch (_: Exception) { 0 }

                    val deliveryDoc = byName[RecordingNaming.deliveryWavName(base)]
                    val deliveryResult = runCatching {
                        byName[RecordingNaming.deliveryJsonName(base)]?.let { d ->
                            app.contentResolver.openInputStream(d.uri)?.use { ins ->
                                DeliveryResult.fromJson(ins.readBytes().decodeToString())
                            }
                        }
                    }.getOrNull()

                    RecordFile(
                        id = UUID.randomUUID().toString(),
                        name = fullName,
                        path = path,
                        durationSeconds = durationSeconds,
                        sceneName = "",
                        hasNoiseReduction = RecordingNaming.hasNr(base),
                        hasEQ = byName.containsKey(RecordingNaming.eqSidecarName(base)),
                        deliveryPath = deliveryDoc?.uri?.toString(),
                        deliveryResult = deliveryResult,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "scanSaf: skipping ${doc.name}: ${e.message}")
                    null
                }
            }
    }

    companion object { private const val TAG = "RecordingScanner" }
}
```

- [ ] **Step 2: Compile (the new class is unused; build stays green)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/data/RecordingScanner.kt
git commit -m "feat: add RecordingScanner collaborator (#13 step 2, not yet wired)"
```

---

## Task 3: Rewire RecorderViewModel to delegate

**Files:**
- Modify: `app/src/main/java/com/example/recorderproject/RecorderViewModel.kt` (field after line ~148; `scanRecordingsFromDisk` 2787-2876; `scanSafRecordings` 2884-2956)

Apply Steps 1-3, then verify in Step 4.

- [ ] **Step 1: Add the scanner field (before the init block that uses it)**

Find (line ~148):

```kotlin
    private val settings = SettingsDataStore(application)
```

Replace with:

```kotlin
    private val settings = SettingsDataStore(application)
    // Library scanning extracted into RecordingScanner (issue #13). Declared before the init
    // block (which calls the scan functions), so it is initialized in time. Only needs `app`.
    private val scanner = com.example.recorderproject.data.RecordingScanner(app)
```

- [ ] **Step 2: Delegate scanRecordingsFromDisk**

Find (lines ~2787-2876):

```kotlin
    private fun scanRecordingsFromDisk() {
        val dir = java.io.File(
            app.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),
            "Recordings",
        )
        if (!dir.exists()) return
        val allWavs = dir.listFiles { f ->
            f.isFile && f.extension.equals("wav", ignoreCase = true)
        }?.toList() ?: return

        // Build set of "base names" that have a _nr companion — we'll hide those originals
        val nrBaseNames = allWavs
            .filter { it.nameWithoutExtension.lowercase().endsWith("_nr") }
            .map { it.nameWithoutExtension.dropLast(3) } // strip "_nr"
            .toSet()

        // Files already in the list (e.g., from PR2 recovery) — don't double-add
        val existingPaths = _recordFiles.value.map { it.path }.toSet()

        val scanned = allWavs
            .filter { f ->
                val base = f.nameWithoutExtension
                val isOriginalShadowedByNr =
                    !base.lowercase().endsWith("_nr") && nrBaseNames.contains(base)
                val isDeliverySibling = base.endsWith("_delivery")
                !isOriginalShadowedByNr && !isDeliverySibling && f.absolutePath !in existingPaths
            }
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f ->
                try {
                    val nameLower = f.nameWithoutExtension.lowercase()
                    val hasNr = nameLower.endsWith("_nr")
                    val eqSidecar = java.io.File(f.parentFile, "${f.nameWithoutExtension}_eq.json")
                    val hasEq = eqSidecar.exists()

                    val durationSeconds = try {
                        val mmr = android.media.MediaMetadataRetriever()
                        mmr.setDataSource(f.absolutePath)
                        val ms = mmr.extractMetadata(
                            android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                        )?.toLongOrNull() ?: 0L
                        mmr.release()
                        (ms / 1000L).toInt()
                    } catch (_: Exception) { 0 }

                    // Pick up delivery sibling if present.
                    val deliveryWav  = java.io.File(f.parentFile, "${f.nameWithoutExtension}_delivery.wav")
                    val deliveryJson = java.io.File(f.parentFile, "${f.nameWithoutExtension}_delivery.json")
                    val deliveryResult = runCatching {
                        if (deliveryJson.exists()) DeliveryResult.fromJson(deliveryJson.readText()) else null
                    }.getOrNull()
                    val deliveryPath = if (deliveryWav.exists()) deliveryWav.absolutePath else null

                    RecordFile(
                        id = java.util.UUID.randomUUID().toString(),
                        name = f.name,
                        path = f.absolutePath,
                        durationSeconds = durationSeconds,
                        sceneName = "",
                        hasNoiseReduction = hasNr,
                        hasEQ = hasEq,
                        deliveryPath = deliveryPath,
                        deliveryResult = deliveryResult,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "scanRecordingsFromDisk: skipping ${f.name}: ${e.message}")
                    null
                }
            }

        // Orphan delivery sweep: _delivery.wav without matching original AND without sidecar JSON.
        val originalNames = allWavs
            .filter { !it.nameWithoutExtension.endsWith("_delivery") }
            .map { it.nameWithoutExtension }
            .toSet()
        allWavs
            .filter { f ->
                val base = f.nameWithoutExtension
                if (!base.endsWith("_delivery")) return@filter false
                val originalBase = base.removeSuffix("_delivery")
                val sidecar = java.io.File(f.parentFile, "${base}.json")
                originalBase !in originalNames && !sidecar.exists()
            }
            .forEach { runCatching { it.delete() } }

        if (scanned.isNotEmpty()) {
            _recordFiles.value = _recordFiles.value + scanned
            Log.i(TAG, "scanRecordingsFromDisk: added ${scanned.size} files from disk")
        }
    }
```

Replace with:

```kotlin
    private fun scanRecordingsFromDisk() {
        val added = scanner.scanDisk(_recordFiles.value.map { it.path }.toSet())
        if (added.isNotEmpty()) {
            _recordFiles.value = _recordFiles.value + added
            Log.i(TAG, "scanRecordingsFromDisk: added ${added.size} files from disk")
        }
    }
```

- [ ] **Step 3: Delegate scanSafRecordings**

Find (lines ~2884-2956):

```kotlin
    private suspend fun scanSafRecordings() = withContext(Dispatchers.IO) {
        val uri = _saveDirectoryUri.value ?: return@withContext
        val tree = try {
            androidx.documentfile.provider.DocumentFile.fromTreeUri(app, uri)
        } catch (_: Exception) { null } ?: return@withContext
        val docs = try { tree.listFiles().toList() } catch (_: Exception) { return@withContext }

        fun baseOf(n: String) = if (n.contains('.')) n.substringBeforeLast('.') else n
        // name -> doc, for sidecar/companion (_eq.json, _delivery.*) lookups
        val byName = docs.mapNotNull { d -> d.name?.let { it to d } }.toMap()

        val wavs = docs.filter { it.isFile && (it.name?.endsWith(".wav", ignoreCase = true) == true) }
        val nrBaseNames = wavs.mapNotNull { it.name }
            .map { baseOf(it) }
            .filter { it.lowercase().endsWith("_nr") }
            .map { it.dropLast(3) }
            .toSet()
        val existingPaths = _recordFiles.value.map { it.path }.toSet()

        val scanned = wavs
            .sortedByDescending { it.lastModified() }
            .mapNotNull { doc ->
                try {
                    val fullName = doc.name ?: return@mapNotNull null
                    val base = baseOf(fullName)
                    val baseLower = base.lowercase()
                    // Hide _delivery siblings and NR-shadowed originals, mirroring the internal scan.
                    if (base.endsWith("_delivery")) return@mapNotNull null
                    if (!baseLower.endsWith("_nr") && nrBaseNames.contains(base)) return@mapNotNull null
                    val path = doc.uri.toString()
                    if (path in existingPaths) return@mapNotNull null

                    val durationSeconds = try {
                        val mmr = android.media.MediaMetadataRetriever()
                        mmr.setDataSource(app, doc.uri)
                        val ms = mmr.extractMetadata(
                            android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                        )?.toLongOrNull() ?: 0L
                        mmr.release()
                        (ms / 1000L).toInt()
                    } catch (_: Exception) { 0 }

                    val deliveryDoc = byName["${base}_delivery.wav"]
                    val deliveryResult = runCatching {
                        byName["${base}_delivery.json"]?.let { d ->
                            app.contentResolver.openInputStream(d.uri)?.use { ins ->
                                DeliveryResult.fromJson(ins.readBytes().decodeToString())
                            }
                        }
                    }.getOrNull()

                    RecordFile(
                        id = java.util.UUID.randomUUID().toString(),
                        name = fullName,
                        path = path,
                        durationSeconds = durationSeconds,
                        sceneName = "",
                        hasNoiseReduction = baseLower.endsWith("_nr"),
                        hasEQ = byName.containsKey("${base}_eq.json"),
                        deliveryPath = deliveryDoc?.uri?.toString(),
                        deliveryResult = deliveryResult,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "scanSafRecordings: skipping ${doc.name}: ${e.message}")
                    null
                }
            }

        if (scanned.isNotEmpty()) {
            _recordFiles.value = _recordFiles.value + scanned
            Log.i(TAG, "scanSafRecordings: added ${scanned.size} files from SAF folder")
        }
    }
```

Replace with:

```kotlin
    private suspend fun scanSafRecordings() = withContext(Dispatchers.IO) {
        val uri = _saveDirectoryUri.value ?: return@withContext
        val added = scanner.scanSaf(uri, _recordFiles.value.map { it.path }.toSet())
        if (added.isNotEmpty()) {
            _recordFiles.value = _recordFiles.value + added
            Log.i(TAG, "scanSafRecordings: added ${added.size} files from SAF folder")
        }
    }
```

- [ ] **Step 4: Compile and run the full unit suite**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all unit tests pass (97 total: prior 87 + 10 new `RecordingNamingTest`).

- [ ] **Step 5: Confirm suite count and zero failures**

Run:
```bash
total=0; fail=0; err=0; for f in app/build/test-results/testDebugUnitTest/*.xml; do t=$(grep -oE 'tests="[0-9]+"' "$f"|head -1|grep -oE '[0-9]+'); fl=$(grep -oE 'failures="[0-9]+"' "$f"|head -1|grep -oE '[0-9]+'); e=$(grep -oE 'errors="[0-9]+"' "$f"|head -1|grep -oE '[0-9]+'); total=$((total+t)); fail=$((fail+fl)); err=$((err+e)); done; echo "tests: $total failures: $fail errors: $err"
```
Expected: `tests: 97 failures: 0 errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/recorderproject/RecorderViewModel.kt
git commit -m "refactor: delegate library scanning to RecordingScanner (#13 step 2)"
```

---

## Device Walkthrough (manual acceptance — run after Task 3)

1. App-storage recordings still appear in the library after relaunch.
2. SAF-folder recordings still appear after relaunch.
3. A recording with an `_nr` twin shows only the NR version (original hidden); an `_eq.json` sidecar shows the EQ badge; a delivery result shows.
4. `_delivery.wav` / `_delivery.json` siblings are not separate list entries.
5. An orphaned `_delivery.wav` (no original, no sidecar) in app storage is deleted on scan.

---

## Self-Review

**Spec coverage:**
- `RecordingNaming` pure rules (baseOf/hasNr/isDeliverySibling/nrShadowedBases/isHidden/sidecar builders/isOrphanDelivery) → Task 1. ✓
- `RecordingScanner` (scanDisk + orphan sweep, scanSaf) returning `List<RecordFile>` → Task 2. ✓
- `_recordFiles` stays in VM; scanner returns lists, VM appends → Task 3 Steps 2-3. ✓
- VM keeps dispatcher wrappers (`scanSafRecordings` stays `suspend`+`withContext(IO)`) → Task 3 Step 3. ✓
- `scanner` declared before the init block that calls the scans → Task 3 Step 1 (placed after `settings`, line ~148; init is ~154). ✓
- No orphan sweep on SAF → Task 2 `scanSaf` has none (matches current). ✓
- Tests: pure rules TDD'd; scanner = compile + suite green → Task 1 + Task 3 Steps 4-5. ✓
- Scope = scanning only, no `_recordFiles` ownership change → no such task. ✓

**Placeholder scan:** none — all code complete; exact commands and expected outputs given.

**Type consistency:** `RecordingNaming` methods (Task 1) are called with matching signatures in `RecordingScanner` (Task 2): `nrShadowedBases(Collection<String>)`, `isHidden(String, Set<String>)`, `hasNr`/`isDeliverySibling`/`baseOf(String)`, `eqSidecarName`/`deliveryWavName`/`deliveryJsonName(String)`, `isOrphanDelivery(String, Set<String>, Boolean)`. `RecordingScanner(app)` constructor (Task 2) matches the call in Task 3 Step 1. `scanDisk(Set<String>)` / `scanSaf(Uri, Set<String>)` signatures match the delegations in Task 3 Steps 2-3. `RecordFile` field names (id/name/path/durationSeconds/sceneName/hasNoiseReduction/hasEQ/deliveryPath/deliveryResult) are copied verbatim from the current code.
