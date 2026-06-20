# FileLibrary extraction (RecorderViewModel decomposition, step 8)

**Date:** 2026-06-20
**Issue:** #13 — `RecorderViewModel` god-object decomposition. Steps 1–7 done (through RecordingController clean half); VM is at ~1,830 lines.
**Status:** Proposed design, pending approval.

## Why now / why this unblocks the rest

The recording lifecycle (`stopRecording`) was deliberately LEFT in the VM at step 7d because its post-production mutates `_recordFiles` (append provisional → swap final), reads `_saveDirectoryUri`, and does cloud backup — i.e. it's fused to the file-library cluster. Extracting `_recordFiles` ownership behind a small mutation API is the prerequisite that lets the lifecycle later move into `RecordingController` calling `fileLibrary.add(...)` instead of reaching into a VM-private flow.

## Scope

Extract into `data/FileLibrary` (alongside `RecordingScanner`/`RecordingNaming`):
- **State:** `_recordFiles`/`recordFiles`; `_sortOrder`/`sortOrder` (+ `sort_order` SharedPreferences persistence); `_searchQuery`/`searchQuery`; `_fileFilter`/`fileFilter`; `_selectedFileIds`/`selectedFileIds`.
- **Derived flows:** `sortedRecordFiles`, `visibleRecordFiles` (the `combine(...).stateIn` pipelines) + the pure `applySort`.
- **Setters:** `setSortOrder`, `setSearchQuery`, `setFileFilter`, `toggleFileSelection`, `clearSelection`, `selectAll`.
- **List-mutation API** (replaces the ~30 `_recordFiles.value = …` sites in the VM):
  - `setAll(list)`, `add(file)`, `addAll(files)` (no-op on empty), `removeById(id)`,
  - `updateById(id) { it.copy(...) }` — for the star/lock/rename/tags/hasEQ single-file maps,
  - `update { list -> … }` — escape hatch for the few odd cases (e.g. delivery `rebindDeliveryResult` matching by `path`),
  - `current(): List<RecordFile>` (or read `recordFiles.value`) — for dedup sets + `selectAll` + `exportSoundReport`.

**Stays in the VM:** `deleteRecording` (disk + companion-file deletion is a VM/IO concern; it calls `fileLibrary.removeById`), `deleteSelected` (orchestration over `deleteRecording`, reading `fileLibrary` state), the scan calls (`scanner` stays; VM does `fileLibrary.addAll(scanner.scanDisk(fileLibrary.current().map{it.path}.toSet()))`), and `markFileHasEq` (becomes `fileLibrary.updateById(id){ it.copy(hasEQ=true) }`).

## Coupling findings

- `_recordFiles` is touched in ~30 places: appends (recovery, trim, new-record placeholder, stop provisional+final), removes (delete), and many `updateById`-shaped maps (star, lock, rename, tags, hasEQ, transcript, pitch-shift, delivery rebind). All map cleanly onto the mutation API above.
- The recording lifecycle's two stop-time mutations (`+ renamedFile` provisional, then `filterNot{id==provisionalId} + finalFile`) become `add(renamedFile)` then `update { it.filterNot{f->f.id==provisionalId} + finalFile }`.
- `rebindDeliveryResult` matches by `path` not `id` → uses the generic `update { }`.
- Persistence: only `sortOrder` persists (to the `meatrec_ui` SharedPreferences). The filter chip is intentionally transient (documented). FileLibrary takes the `SharedPreferences` so it owns the `sort_order` read/write.
- Derived flows need `viewModelScope` for `stateIn(SharingStarted.Eagerly)`.

## FileFilter enum — relocate to `model/`

`FileFilter` is currently nested in `RecorderViewModel` and referenced as `RecorderViewModel.FileFilter` by **5 UI files** (`MeatRecHome`, `RecorderApp`, `LibraryPage`, `RecordingsToolbar`, `RecordingsSearchAndFilter`). To avoid a `data → viewmodel` back-dependency from FileLibrary, **move it to `model/FileFilter.kt`** (the correct home — `SortOrder` already lives in `model/`). Update the UI references (mechanical: `com.example.recorderproject.RecorderViewModel.FileFilter` → `com.example.recorderproject.model.FileFilter`). Behavior-identical; only the type's package changes.

## `FileLibrary` shape

New file `app/src/main/java/com/example/recorderproject/data/FileLibrary.kt`.

```kotlin
class FileLibrary(
    private val scope: CoroutineScope,
    private val prefs: SharedPreferences,
) {
    private val _recordFiles = MutableStateFlow<List<RecordFile>>(emptyList())
    val recordFiles: StateFlow<List<RecordFile>> = _recordFiles
    // sortOrder (persisted), searchQuery, fileFilter, selectedFileIds … (moved verbatim)
    val sortedRecordFiles: StateFlow<List<RecordFile>> = combine(_recordFiles, _sortOrder){…}.stateIn(scope, Eagerly, emptyList())
    val visibleRecordFiles: StateFlow<List<RecordFile>> = combine(_recordFiles,_sortOrder,_searchQuery,_fileFilter){…}.stateIn(scope, Eagerly, emptyList())

    fun setSortOrder(o); fun setSearchQuery(q); fun setFileFilter(f)
    fun toggleFileSelection(id); fun clearSelection(); fun selectAll()
    fun setAll(l); fun add(f); fun addAll(l); fun removeById(id)
    fun updateById(id, transform: (RecordFile)->RecordFile); fun update(transform: (List<RecordFile>)->List<RecordFile>)
    fun current(): List<RecordFile> = _recordFiles.value
    private fun applySort(files, order)
}
```

### ViewModel after extraction
- Field `private val fileLibrary = FileLibrary(viewModelScope, prefs)`. Re-expose all flows under identical names (`recordFiles`, `sortOrder`, `searchQuery`, `fileFilter`, `selectedFileIds`, `sortedRecordFiles`, `visibleRecordFiles`). Delegate the 6 setters.
- Replace every `_recordFiles.value = …` with the matching `fileLibrary` mutator; every `_recordFiles.value` read with `fileLibrary.current()` (or `recordFiles.value`).
- Keep `deleteRecording`/`deleteSelected` in the VM, routed through `fileLibrary`.

## Verification

Per the decomposition template: clean compile + full existing unit suite (120) green + device smoke (library lists takes, sort/filter/search chips work, star/lock/rename reflect, multi-select delete). No new unit tests (the pure `applySort` is trivial and moves verbatim).
