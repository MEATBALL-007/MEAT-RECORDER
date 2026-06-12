package com.example.recorderproject.model

import com.example.recorderproject.billing.ProFeature

/**
 * The in-recording quick controls a user can show/hide and reorder. Declaration
 * order is the default layout order. [proFeature] (nullable) marks pro-gated
 * controls so the UI can badge and gate them.
 */
enum class QuickControl(
    val id: String,
    val label: String,
    val proFeature: ProFeature?,
) {
    PAUSE("pause", "Pause", null),
    DROP_CUE("drop_cue", "Drop cue", null),
    SLATE("slate", "Slate", ProFeature.PRE_ROLL_VAD),
    LIVE_EQ("live_eq", "Live EQ", ProFeature.FULL_EQ),
    NR_GATE("nr_gate", "NR Gate", ProFeature.LIVE_DSP),
    EDIT_EQ("edit_eq", "Edit EQ", ProFeature.FULL_EQ),
    PRE_ROLL("pre_roll", "Pre-roll 5s", ProFeature.PRE_ROLL_VAD),
    VAD("vad", "VAD", ProFeature.PRE_ROLL_VAD);

    companion object {
        fun fromId(id: String): QuickControl? = entries.firstOrNull { it.id == id }
    }
}
