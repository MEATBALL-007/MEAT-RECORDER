package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MeatYellow = Color(0xFFFFC72C)

/**
 * Two-segment pill toggle for the home section pager (Record | Library).
 * Stateless: [selectedIndex] is the highlighted segment; tapping segment i calls [onSelect](i).
 * Tappable companion to the swipe gesture — keeps the pager reachable without a swipe (a11y).
 */
@Composable
fun HomeSegmentedControl(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    labels: List<String> = listOf("Record", "Library"),
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val isSel = i == selectedIndex
            Text(
                text = label,
                color = if (isSel) Color(0xFF0C0C10) else Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSel) MeatYellow else Color.Transparent)
                    .selectable(selected = isSel, role = Role.Tab) { onSelect(i) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101014)
@Composable
private fun HomeSegmentedControlRecordPreview() {
    HomeSegmentedControl(selectedIndex = 0, onSelect = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF101014)
@Composable
private fun HomeSegmentedControlLibraryPreview() {
    HomeSegmentedControl(selectedIndex = 1, onSelect = {})
}
