package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorderproject.RecorderViewModel
import com.example.recorderproject.model.SortOrder

private val MeatOrange = Color(0xFFFA4616)
private val MeatYellow = Color(0xFFFFC72C)

/**
 * Toolbar above the Recordings list — Batch 4 polish.
 *
 *  ┌────────────────────────────────────────┐
 *  │ 🔍 [search field            ] [×]      │
 *  ├────────────────────────────────────────┤
 *  │ [All] [★] [🔒] [NR] [EQ]    [↕ Sort ▾] │
 *  └────────────────────────────────────────┘
 *
 *  Search field uses BasicTextField (inline) with magnifier + clear-x.
 *  Filter chips horizontally scroll on small screens.
 *  Sort dropdown opens a DropdownMenu with the 6 SortOrder entries.
 */
@Composable
fun RecordingsToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    currentFilter: com.example.recorderproject.model.FileFilter,
    onFilterChange: (com.example.recorderproject.model.FileFilter) -> Unit,
    currentSort: SortOrder,
    onSortChange: (SortOrder) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Search row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1F1F1F))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconLineSearch(tint = Color.White.copy(alpha = 0.45f), size = 16.dp)
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 15.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(MeatOrange),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search recordings…",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 15.sp,
                            )
                        }
                        inner()
                    },
                )
            }
            if (searchQuery.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { onSearchChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    IconLineX(tint = Color.White.copy(alpha = 0.7f), size = 12.dp)
                }
            }
        }

        // Filter chips + sort
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip("All", com.example.recorderproject.model.FileFilter.ALL, currentFilter, onFilterChange)
                FilterChip("★ Starred", com.example.recorderproject.model.FileFilter.STARRED, currentFilter, onFilterChange)
                FilterChip("Locked", com.example.recorderproject.model.FileFilter.LOCKED, currentFilter, onFilterChange)
                FilterChip("NR", com.example.recorderproject.model.FileFilter.NR, currentFilter, onFilterChange)
                FilterChip("EQ", com.example.recorderproject.model.FileFilter.EQ, currentFilter, onFilterChange)
            }
            SortDropdown(currentSort = currentSort, onSortChange = onSortChange)
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    value: com.example.recorderproject.model.FileFilter,
    current: com.example.recorderproject.model.FileFilter,
    onSelect: (com.example.recorderproject.model.FileFilter) -> Unit,
) {
    val active = value == current
    Text(
        label,
        color = if (active) Color.White else Color.White.copy(alpha = 0.55f),
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) MeatOrange else Color(0xFF1F1F1F))
            .clickable { onSelect(value) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun SortDropdown(currentSort: SortOrder, onSortChange: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1F1F1F))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("↕", color = MeatYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                currentSort.displayName.take(10),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("▾", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            order.displayName,
                            color = if (order == currentSort) MeatOrange else Color.White,
                            fontWeight = if (order == currentSort) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = { onSortChange(order); open = false },
                )
            }
        }
    }
}
