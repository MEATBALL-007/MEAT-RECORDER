package com.example.recorderproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.model.RecordFile
import com.example.recorderproject.model.SortOrder

/**
 * The "Library" page of the home pager: bulk-select action bar, recordings
 * search/filter/sort toolbar, and the recordings list. Pure presentation.
 */
@Composable
fun LibraryPage(
    files: List<RecordFile>,
    selectedFileId: String?,
    isPlaying: Boolean,
    onTapFile: (RecordFile) -> Unit,
    onShareFile: (RecordFile) -> Unit,
    onRenameFile: (RecordFile, String) -> Unit,
    onToggleStarFile: (RecordFile) -> Unit,
    onToggleLockFile: (RecordFile) -> Unit,
    onDeleteFile: (RecordFile) -> Unit,
    onTrimFile: (RecordFile) -> Unit,
    onEQFile: (RecordFile) -> Unit,
    selectedIds: Set<String>,
    onClearSelection: () -> Unit,
    onBulkDelete: () -> Unit,
    onBulkCompareAb: () -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    fileFilter: com.example.recorderproject.model.FileFilter,
    onFilterChange: (com.example.recorderproject.model.FileFilter) -> Unit,
    sortOrder: SortOrder,
    onSortChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Q6: bulk-select action bar — only visible when items are selected
        if (selectedIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeatOrange.copy(alpha = 0.18f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${selectedIds.size} selected",
                    color = MeatOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Clear",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(onClick = onClearSelection)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                    if (selectedIds.size == 2) {
                        Text(
                            "A/B",
                            color = Color(0xFFFFC72C),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable(onClick = onBulkCompareAb)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Text(
                        "Delete",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MeatOrange)
                            .clickable(onClick = onBulkDelete)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Batch 4: Toolbar above the recordings list (search + filter + sort).
        // Also show it when a filter/search is ACTIVE even if the resulting list is
        // empty — otherwise a filter that hides everything (e.g. "NR") would also hide
        // the only control that can reset it, leaving the library stuck looking empty.
        if (files.isNotEmpty() || searchQuery.isNotEmpty() ||
            fileFilter != com.example.recorderproject.model.FileFilter.ALL
        ) {
            com.example.recorderproject.ui.components.RecordingsToolbar(
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                currentFilter = fileFilter,
                onFilterChange = onFilterChange,
                currentSort = sortOrder,
                onSortChange = onSortChange,
            )
        }

        // L1: Recordings list card — Batch 1, file library
        com.example.recorderproject.ui.components.RecordingsListCard(
            files = files,
            selectedId = selectedFileId,
            isPlaying = isPlaying,
            onTap = onTapFile,
            onShare = onShareFile,
            onRename = onRenameFile,
            onToggleStar = onToggleStarFile,
            onToggleLock = onToggleLockFile,
            onDelete = onDeleteFile,
            onOpenTrim = onTrimFile,
            onOpenEQ = onEQFile,
        )

        // Padding at the bottom so the MiniPlayer doesn't cover the last row
        Box(modifier = Modifier.height(80.dp))
    }
}
