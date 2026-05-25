package com.example.recorderproject.model

data class RecordFile(
    val id: String,
    val name: String,
    val path: String,
    val durationSeconds: Int,
    val sceneName: String,
    val notes: String = "",
    val tags: String = "",
    val isLocked: Boolean = false,
    val hasNoiseReduction: Boolean = false,
    val sampleRate: Int = 48000,
    val channelCount: Int = 1,
    val bitDepth: Int = 16,
    val hasEQ: Boolean = false,
    val syncPointMs: Long? = null,
    val locationTag: String? = null,
    val environmentTag: String? = null,
    val starred: Boolean = false,
    val cuePoints: List<CuePoint> = emptyList(),
)
