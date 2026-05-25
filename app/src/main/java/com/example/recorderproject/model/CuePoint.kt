package com.example.recorderproject.model

/** Phase 4 — a marker dropped during a recording. */
data class CuePoint(
    val timeMs: Long,
    val label: String = "",
)
