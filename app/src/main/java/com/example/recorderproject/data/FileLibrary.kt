package com.example.recorderproject.data

import android.content.SharedPreferences
import com.example.recorderproject.model.FileFilter
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.model.SortOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Owns the in-memory recordings list and everything that shapes its presentation —
 * sort order (persisted), search query, filter chip, and bulk-selection — plus the derived
 * sorted/visible flows the UI observes. Extracted from RecorderViewModel (issue #13 step 8).
 *
 * The ViewModel mutates the list only through the small API here ([add]/[addAll]/[removeById]/
 * [updateById]/[update]/[setAll]); this is what lets the recording lifecycle later move into
 * RecordingController calling `fileLibrary.add(...)` instead of reaching into a private flow.
 *
 * @param scope the ViewModel scope, used to keep the derived flows hot via stateIn.
 * @param prefs the `meatrec_ui` SharedPreferences, owning the persisted `sort_order` key.
 */
class FileLibrary(
    private val scope: CoroutineScope,
    private val prefs: SharedPreferences,
) {
    private val _recordFiles = MutableStateFlow<List<RecordFile>>(emptyList())
    val recordFiles: StateFlow<List<RecordFile>> = _recordFiles

    private val _sortOrder = MutableStateFlow(
        runCatching { SortOrder.valueOf(prefs.getString("sort_order", null) ?: "") }
            .getOrElse { SortOrder.Default }
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    /**
     * Files sorted per [sortOrder]. UI should observe this, not [recordFiles].
     * Date sort uses insertion order as a proxy (the list appends on new takes);
     * `RecordFile` has no explicit timestamp field today.
     */
    val sortedRecordFiles: StateFlow<List<RecordFile>> = combine(_recordFiles, _sortOrder) { files, order ->
        applySort(files, order)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        prefs.edit().putString("sort_order", order.name).apply()
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    // NOTE: the filter chip is TRANSIENT view state and is intentionally NOT restored
    // across launches. Persisting it caused a trap: a stuck non-ALL filter (e.g. "NR")
    // hid every recording, and since the filter toolbar only shows when the list is
    // non-empty, there was no way to switch back to "All" — the library looked empty
    // forever. Always start at ALL so recordings are visible by default.
    private val _fileFilter = MutableStateFlow(FileFilter.ALL)
    val fileFilter: StateFlow<FileFilter> = _fileFilter
    fun setFileFilter(f: FileFilter) { _fileFilter.value = f }

    // Bulk multi-select state for the recordings list.
    private val _selectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFileIds: StateFlow<Set<String>> = _selectedFileIds

    fun toggleFileSelection(id: String) {
        _selectedFileIds.value = _selectedFileIds.value.toMutableSet().also {
            if (!it.add(id)) it.remove(id)
        }
    }
    fun clearSelection() { _selectedFileIds.value = emptySet() }
    fun selectAll() { _selectedFileIds.value = _recordFiles.value.map { it.id }.toSet() }

    /**
     * Files filtered by [searchQuery] (case-insensitive substring of name) AND [fileFilter]
     * chip, then sorted per [sortOrder]. UI should observe this.
     */
    val visibleRecordFiles: StateFlow<List<RecordFile>> = combine(
        _recordFiles, _sortOrder, _searchQuery, _fileFilter,
    ) { files, order, query, filter ->
        val matched = files.filter { f ->
            val matchesQuery = query.isBlank() || f.name.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                FileFilter.ALL -> true
                FileFilter.STARRED -> f.starred
                FileFilter.LOCKED -> f.isLocked
                FileFilter.NR -> f.hasNoiseReduction
                FileFilter.EQ -> f.hasEQ
            }
            matchesQuery && matchesFilter
        }
        applySort(matched, order)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun applySort(files: List<RecordFile>, order: SortOrder): List<RecordFile> = when (order) {
        SortOrder.DATE_NEWEST    -> files.asReversed()
        SortOrder.DATE_OLDEST    -> files
        SortOrder.NAME_ASC       -> files.sortedBy { it.name.lowercase() }
        SortOrder.NAME_DESC      -> files.sortedByDescending { it.name.lowercase() }
        SortOrder.DURATION_LONG  -> files.sortedByDescending { it.durationSeconds }
        SortOrder.DURATION_SHORT -> files.sortedBy { it.durationSeconds }
    }

    // ---- List mutation API (the only way the list changes) ----

    /** Snapshot of the current list (for dedup sets, select-all, reports). */
    fun current(): List<RecordFile> = _recordFiles.value

    fun setAll(list: List<RecordFile>) { _recordFiles.value = list }
    fun add(file: RecordFile) { _recordFiles.value = _recordFiles.value + file }
    fun addAll(files: List<RecordFile>) { if (files.isNotEmpty()) _recordFiles.value = _recordFiles.value + files }
    fun removeById(id: String) { _recordFiles.value = _recordFiles.value.filter { it.id != id } }

    /** Replace the single file with [id] via [transform]; others pass through unchanged. */
    fun updateById(id: String, transform: (RecordFile) -> RecordFile) {
        _recordFiles.value = _recordFiles.value.map { if (it.id == id) transform(it) else it }
    }

    /** Escape hatch for whole-list rewrites (e.g. matching by path, provisional swap). */
    fun update(transform: (List<RecordFile>) -> List<RecordFile>) {
        _recordFiles.value = transform(_recordFiles.value)
    }
}
