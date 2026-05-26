package com.example.recorderproject.model

/**
 * Recording-file list sort order.
 *
 * Shape restored from the 2026-05-22 APK pulled off the user's S24 Ultra during the
 * APK-recovery pass on 2026-05-26 (decompiled via jadx; see `recovery/INVENTORY.md`).
 * Wires into `RecorderViewModel.filteredSortedFiles` once the file-list sort/search UI lands
 * (Phase 3 roadmap item).
 *
 * The old enum lived at top-level `com.example.recorderproject.SortOrder` — moved into
 * `model/` here for consistency with the rest of the data layer.
 */
enum class SortOrder(val displayName: String) {
    DATE_NEWEST("Newest first"),
    DATE_OLDEST("Oldest first"),
    NAME_ASC("Name A→Z"),
    NAME_DESC("Name Z→A"),
    DURATION_LONG("Longest first"),
    DURATION_SHORT("Shortest first");

    companion object {
        val Default = DATE_NEWEST
    }
}
