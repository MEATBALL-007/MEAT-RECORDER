package com.example.recorderproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.recorderproject.model.SortOrder
import com.example.recorderproject.ui.theme.LocalAppTypography
import com.example.recorderproject.ui.theme.RecorderBlueGrey
import com.example.recorderproject.ui.theme.RecorderYellow
import com.example.recorderproject.ui.theme.Spacing
import com.example.recorderproject.ui.theme.SurfaceContainerHigh

/**
 * Compact inline sort picker for the file list section header.
 *
 *   [Newest ▾]   ← tappable chip, opens DropdownMenu of all SortOrder values
 *
 * Lives in the RECORDINGS header alongside the count badge. Uses `labelTiny` so it
 * visually pairs with the caps section label.
 */
@Composable
fun SortPicker(
    current: SortOrder,
    onChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceContainerHigh)
            .clickable { expanded = true }
            .padding(horizontal = Spacing.sm, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = current.displayName,
            style = LocalAppTypography.current.labelTiny,
            color = RecorderYellow,
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = "Change sort order",
            tint = RecorderBlueGrey,
            modifier = Modifier.size(14.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            order.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (order == current) RecorderYellow else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onChange(order)
                        expanded = false
                    },
                )
            }
        }
    }
}

