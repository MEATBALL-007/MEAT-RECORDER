package com.example.recorderproject.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Theme palettes — 10 dark variants port-back from old MEATrec AppTheme.kt.
 *
 * Each theme defines a background / surface / surfaceElevated triple. The brand
 * accent colors (primary = Orange, secondary = Yellow) stay constant across all
 * themes — only the dark base shifts.
 *
 * Values decoded from decompiled AppTheme.java (Color.Long → ARGB hex).
 */
enum class AppTheme(
    val displayName: String,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
) {
    MIDNIGHT ("Midnight", Color(0xFF0C0C0D), Color(0xFF19191A), Color(0xFF202425)),
    NAVY     ("Navy",     Color(0xFF07111F), Color(0xFF0B1A30), Color(0xFF142640)),
    FOREST   ("Forest",   Color(0xFF051908), Color(0xFF0A2110), Color(0xFF173E18)),
    MAROON   ("Maroon",   Color(0xFF1A0808), Color(0xFF270E0E), Color(0xFF361414)),
    CARBON   ("Carbon",   Color(0xFF161614), Color(0xFF1F1F20), Color(0xFF2E2D2C)),
    VIOLET   ("Violet",   Color(0xFF111118), Color(0xFF1B1928), Color(0xFF252338)),
    STEEL    ("Steel",    Color(0xFF0A1018), Color(0xFF0F151F), Color(0xFF161D28)),
    AMBER    ("Amber",    Color(0xFF1A1300), Color(0xFF271D00), Color(0xFF362700)),
    SLATE    ("Slate",    Color(0xFF14181F), Color(0xFF1E222A), Color(0xFF272D33)),
    OBSIDIAN ("Obsidian", Color(0xFF05060A), Color(0xFF0E1018), Color(0xFF181B24)),
    ;

    companion object {
        val Default = MIDNIGHT

        /** Look up by display name, case-insensitive. Returns [Default] on miss. */
        fun fromName(name: String?): AppTheme =
            entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: Default
    }
}
