package com.example.recorderproject.model

sealed class LoudnessTarget {

    abstract val targetLufs: Float?
    abstract val tpCeilingDbtp: Float?
    abstract val displayName: String

    object Off : LoudnessTarget() {
        override val targetLufs: Float? = null
        override val tpCeilingDbtp: Float? = null
        override val displayName = "Off"
    }

    object Streaming : LoudnessTarget() {
        override val targetLufs = -14f
        override val tpCeilingDbtp = -1f
        override val displayName = "Streaming –14"
    }

    object Podcast : LoudnessTarget() {
        override val targetLufs = -16f
        override val tpCeilingDbtp = -1f
        override val displayName = "Podcast –16"
    }

    object Broadcast : LoudnessTarget() {
        override val targetLufs = -23f
        override val tpCeilingDbtp = -1f
        override val displayName = "Broadcast –23"
    }

    data class Custom(val lufs: Float, val tpDbtp: Float) : LoudnessTarget() {
        override val targetLufs get() = lufs
        override val tpCeilingDbtp get() = tpDbtp
        override val displayName: String get() {
            val raw = "%.1f".format(lufs).trimEnd('0').trimEnd('.')
            val pretty = if (raw.startsWith("-")) "–" + raw.substring(1) else raw
            return "Custom $pretty"
        }
    }

    companion object {
        val DEFAULT: LoudnessTarget = Off

        fun encode(t: LoudnessTarget): Triple<String, Float, Float> = when (t) {
            Off        -> Triple("OFF",       0f, 0f)
            Streaming  -> Triple("STREAMING", -14f, -1f)
            Podcast    -> Triple("PODCAST",   -16f, -1f)
            Broadcast  -> Triple("BROADCAST", -23f, -1f)
            is Custom  -> Triple("CUSTOM",    t.lufs, t.tpDbtp)
        }

        fun decode(key: String?, lufs: Float, tp: Float): LoudnessTarget = when (key) {
            "OFF"       -> Off
            "STREAMING" -> Streaming
            "PODCAST"   -> Podcast
            "BROADCAST" -> Broadcast
            "CUSTOM"    -> Custom(lufs, tp)
            else        -> DEFAULT
        }
    }
}
