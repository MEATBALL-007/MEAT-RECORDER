package com.example.recorderproject.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing tokens — 4-pt grid (Material default), with one 2-dp sub-grid token for tight gaps.
 *
 * Import directly: `import com.example.recorderproject.ui.theme.Spacing`
 * Use: `Modifier.padding(Spacing.md)`, `Arrangement.spacedBy(Spacing.sm)`, etc.
 *
 * No theme indirection — these are constants, not theme-dependent.
 */
object Spacing {
    val xxs = 2.dp     // sub-grid (used sparingly; e.g., 2-dp gap between paired icons)
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 12.dp    // default card padding
    val lg  = 16.dp    // screen edge padding
    val xl  = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}
