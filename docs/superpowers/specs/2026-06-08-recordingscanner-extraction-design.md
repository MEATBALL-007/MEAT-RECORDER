# RecordingScanner + RecordingNaming extraction (RecorderViewModel decomposition, step 2)

**Date:** 2026-06-08
**Issue:** #13 — `RecorderViewModel` god-object decomposition. Step 1 (`LoudnessManager`) is done.
**Status:** Approved design, pending implementation plan.

## Problem

`RecorderViewModel` still contains ~170 lines of library-scanning logic: `scanRecordingsFromDisk()` (internal app-storage) and `scanSafRecordings()` (SAF folder). Both reconstruct the recording list from disk/SAF on launch, applying naming conventions (`_nr` noise-reduction twins, `_delivery` siblings, `_eq.json`/`_delivery.json` sidecars) and an orphan-delivery cleanup sweep. These conventions are non-obvious and currently untested.

This is step 2 of the decomposition. Unlike step 1 (a pure move), this cluster contains genuinely pure decision rules worth extracting and testing.

## Scope

Scanning only. `_recordFiles` (the central library StateFlow, consumed by sorting/filtering and mutated by record/NR/delete) **stays in the ViewModel** — the scanner returns `List<RecordFile>` and the ViewModel appends, exactly as today. Ownership of `_recordFiles` is a later, larger "FileLibrary state" step, out of scope here.

## Design

Two new units in `app/src/main/java/com/example/recorderproject/data/` (alongside `SettingsDataStore`; scanning the filesystem/SAF for records is a data-layer concern):

### `RecordingNaming` (pure object — unit tested)

Holds every naming/filtering rule with no Android types, so it is fully JVM-unit-testable. Reproduces the current logic exactly.

```kotlin
object RecordingNaming {
    fun baseOf(name: String): String                    // name without extension
    fun hasNr(base: String): Boolean                     // base.lowercase().endsWith("_nr")
    fun isDeliverySibling(base: String): Boolean         // base.endsWith("_delivery")
    fun nrShadowedBases(wavBaseNames: Collection<String>): Set<String>
    fun isHidden(base: String, nrShadowed: Set<String>): Boolean
    fun eqSidecarName(base: String): String              // "${base}_eq.json"
    fun deliveryWavName(base: String): String            // "${base}_delivery.wav"
    fun deliveryJsonName(base: String): String           // "${base}_delivery.json"
    fun isOrphanDelivery(base: String, originalBases: Set<String>, hasSidecarJson: Boolean): Boolean
}
```

Rule semantics (matching the current code):
- `baseOf`: `if (name.contains('.')) name.substringBeforeLast('.') else name`.
- `nrShadowedBases`: from the wav base names, take those whose lowercase ends with `_nr`, strip the trailing `_nr` (3 chars), collect to a set — the original base names to hide.
- `isHidden`: `isDeliverySibling(base) || (!hasNr(base) && nrShadowed.contains(base))`. (Existing-path dedup is path-based, not name-based, and stays in the scanner.)
- `isOrphanDelivery`: only for delivery siblings; `base.removeSuffix("_delivery") !in originalBases && !hasSidecarJson`.

### `RecordingScanner` (Android collaborator)

```kotlin
class RecordingScanner(private val app: Context) {
    fun scanDisk(existingPaths: Set<String>): List<RecordFile>          // includes the orphan-delivery sweep (deletes)
    fun scanSaf(treeUri: Uri, existingPaths: Set<String>): List<RecordFile>
}
```

- `scanDisk`: reads `getExternalFilesDir(DIRECTORY_MUSIC)/Recordings`; returns `emptyList()` if absent. Lists `*.wav`, builds `nrShadowedBases`, filters with `isHidden` + `existingPaths`, sorts by `lastModified` descending, reads duration via `MediaMetadataRetriever`, detects `hasEQ` via the `_eq.json` file existing, picks up `_delivery.wav`/`_delivery.json` siblings (parsing `DeliveryResult`), and performs the orphan-delivery sweep (deleting orphaned `_delivery.wav` files via `isOrphanDelivery`). Returns the scanned list.
- `scanSaf`: `DocumentFile.fromTreeUri`; lists documents into a name→doc map; same filtering via `RecordingNaming`; duration via `MediaMetadataRetriever.setDataSource(app, uri)`; `hasEQ` via the `_eq.json` key present in the map; delivery sibling via the map; `DeliveryResult` parsed from the `_delivery.json` document stream. Returns the scanned list. (No orphan sweep on SAF — matches current behavior.)
- Both move the current bodies verbatim except that rule decisions call `RecordingNaming`. Per-file skip logging uses the scanner's own tag.

### ViewModel changes (public surface unchanged — both scan functions are private)

```kotlin
private val scanner = RecordingScanner(app)

private fun scanRecordingsFromDisk() {
    val added = scanner.scanDisk(_recordFiles.value.map { it.path }.toSet())
    if (added.isNotEmpty()) {
        _recordFiles.value = _recordFiles.value + added
        Log.i(TAG, "scanRecordingsFromDisk: added ${added.size} files from disk")
    }
}

private suspend fun scanSafRecordings() = withContext(Dispatchers.IO) {
    val uri = _saveDirectoryUri.value ?: return@withContext
    val added = scanner.scanSaf(uri, _recordFiles.value.map { it.path }.toSet())
    if (added.isNotEmpty()) {
        _recordFiles.value = _recordFiles.value + added
        Log.i(TAG, "scanSafRecordings: added ${added.size} files from SAF folder")
    }
}
```

The ViewModel keeps the dispatcher wrappers (`scanSafRecordings` stays `suspend` + `withContext(Dispatchers.IO)`) and the `_recordFiles` mutation. ~170 lines of I/O move out.

## Net effect

~170 lines leave the ViewModel into a focused `RecordingScanner` (~150 lines) plus a small tested `RecordingNaming`. The ViewModel's public surface is unchanged.

## Testing

- **`RecordingNamingTest` (JVM, TDD):** baseOf (with/without extension); hasNr; isDeliverySibling; nrShadowedBases (twin present → original base in set; no twin → empty); isHidden (delivery sibling → true; NR-shadowed original → true; an `_nr` file itself → false; unrelated → false); the three sidecar name builders; isOrphanDelivery (orphan → true; original still present → false; sidecar JSON present → false; non-delivery name → false).
- **`RecordingScanner`:** Android- and I/O-coupled (Context, File, DocumentFile, MediaMetadataRetriever, ContentResolver) — not JVM-unit-testable in this project (no Robolectric). Verified by clean compile + the full existing unit suite staying green.

### Device smoke (acceptance)

1. App-storage recordings still appear in the library after relaunch.
2. SAF-folder recordings still appear after relaunch.
3. A recording with an `_nr` twin shows only the NR version (original hidden); an `_eq.json` sidecar shows the EQ badge; a `_delivery` result shows.
4. `_delivery.wav` and `_delivery.json` siblings do not appear as separate list entries.
5. An orphaned `_delivery.wav` (no original, no sidecar) in app storage is cleaned up on scan.

## Out of scope

- Moving `_recordFiles` ownership or the sorting/filtering/selection logic out of the ViewModel — a later FileLibrary-state step.
- Any behavior change to scanning, naming, or the orphan sweep — this is a behavior-preserving extraction.
- Adding Robolectric or instrumented tests for the scanner.
