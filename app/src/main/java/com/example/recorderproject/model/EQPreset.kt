package com.example.recorderproject.model

data class EQPreset(
    val name: String,
    val description: String,
    val category: PresetCategory,
    val bands: List<EQBand>,
)

object EQPresets {

    private fun band(id: Int, type: EQBandType, hz: Float, db: Float, q: Float, on: Boolean = true): EQBand =
        EQBand(id = id, type = type, frequencyHz = hz, gainDb = db, q = q, enabled = on)

    private fun off(id: Int, hz: Float): EQBand =
        EQBand(id = id, type = EQBandType.BELL, frequencyHz = hz, gainDb = 0f, q = 1f, enabled = false)

    private val Flat = EQPreset(
        name = "Flat",
        description = "All bands disabled — true bypass for A/B reference",
        category = PresetCategory.NEUTRAL,
        bands = listOf(
            off(1, 60f), off(2, 150f), off(3, 320f), off(4, 700f),
            off(5, 1500f), off(6, 3200f), off(7, 7000f), off(8, 12000f),
        ),
    )

    private val AirLift = EQPreset(
        name = "Air Lift",
        description = "Light high-shelf opening — FabFilter Pro-Q-style sparkle",
        category = PresetCategory.MASTER,
        bands = listOf(
            band(1, EQBandType.HIGH_PASS, 30f, 0f, 0.707f),
            off(2, 150f), off(3, 320f), off(4, 700f), off(5, 1500f),
            band(6, EQBandType.BELL, 4000f, 1.5f, 1.2f),
            band(7, EQBandType.HIGH_SHELF, 8000f, 3f, 0.7f),
            band(8, EQBandType.HIGH_SHELF, 14000f, 2.5f, 0.5f),
        ),
    )

    private val VintageConsole = EQPreset(
        name = "Vintage Console",
        description = "SSL E-Channel-style mid scoop + smooth highs",
        category = PresetCategory.VINTAGE,
        bands = listOf(
            band(1, EQBandType.HIGH_PASS, 60f, 0f, 0.707f),
            band(2, EQBandType.LOW_SHELF, 120f, 2f, 0.7f),
            band(3, EQBandType.BELL, 350f, -2.5f, 1.0f),
            band(4, EQBandType.BELL, 900f, -1.5f, 1.2f),
            off(5, 1500f),
            band(6, EQBandType.BELL, 4500f, 2f, 1.4f),
            band(7, EQBandType.HIGH_SHELF, 10000f, 2f, 0.7f),
            off(8, 14000f),
        ),
    )

    private val PultecSmooth = EQPreset(
        name = "Pultec Smooth",
        description = "Pultec EQP-1A signature: bass boost-and-cut at 100 Hz + 16 kHz silk",
        category = PresetCategory.VINTAGE,
        bands = listOf(
            band(1, EQBandType.LOW_SHELF, 100f, 3f, 0.6f),
            band(2, EQBandType.BELL, 100f, -2f, 1.5f),
            off(3, 320f), off(4, 700f), off(5, 1500f), off(6, 3200f),
            band(7, EQBandType.BELL, 8000f, 1f, 0.9f),
            band(8, EQBandType.HIGH_SHELF, 16000f, 4f, 0.5f),
        ),
    )

    private val ApiPunch = EQPreset(
        name = "API Punch",
        description = "Drum-oriented punch — low thump + 5 kHz attack",
        category = PresetCategory.DRUM,
        bands = listOf(
            band(1, EQBandType.HIGH_PASS, 40f, 0f, 0.707f),
            band(2, EQBandType.BELL, 100f, 3.5f, 1.0f),
            band(3, EQBandType.BELL, 400f, -2f, 1.0f),
            off(4, 700f), off(5, 1500f),
            band(6, EQBandType.BELL, 5000f, 3f, 1.2f),
            band(7, EQBandType.BELL, 8000f, 1.5f, 1.0f),
            off(8, 14000f),
        ),
    )

    private val MassiveLow = EQPreset(
        name = "Massive Low",
        description = "Manley-Massive-Passive-style wide warm low boost",
        category = PresetCategory.VINTAGE,
        bands = listOf(
            band(1, EQBandType.LOW_SHELF, 80f, 4f, 0.5f),
            band(2, EQBandType.BELL, 200f, 1.5f, 0.7f),
            off(3, 320f), off(4, 700f), off(5, 1500f), off(6, 3200f),
            band(7, EQBandType.BELL, 8000f, 1f, 0.7f),
            off(8, 14000f),
        ),
    )

    private val NeveWarmth = EQPreset(
        name = "Neve Warmth",
        description = "1073-style vintage musical curves",
        category = PresetCategory.VINTAGE,
        bands = listOf(
            band(1, EQBandType.HIGH_PASS, 50f, 0f, 0.707f),
            band(2, EQBandType.LOW_SHELF, 220f, 2.5f, 0.6f),
            band(3, EQBandType.BELL, 700f, -1f, 1.0f),
            band(4, EQBandType.BELL, 1600f, 1.5f, 1.0f),
            off(5, 1500f),
            band(6, EQBandType.BELL, 4000f, 2.5f, 1.2f),
            band(7, EQBandType.HIGH_SHELF, 12000f, 2f, 0.5f),
            off(8, 14000f),
        ),
    )

    private val BroadcastVoice = EQPreset(
        name = "Broadcast Voice",
        description = "High-pass + presence boost for spoken word",
        category = PresetCategory.VOCAL,
        bands = listOf(
            band(1, EQBandType.HIGH_PASS, 80f, 0f, 0.707f),
            band(2, EQBandType.BELL, 200f, -2f, 1.0f),
            band(3, EQBandType.BELL, 350f, -1.5f, 1.2f),
            off(4, 700f),
            band(5, EQBandType.BELL, 2500f, 2f, 1.0f),
            band(6, EQBandType.BELL, 5000f, 3f, 1.2f),
            band(7, EQBandType.HIGH_SHELF, 8000f, 1.5f, 0.7f),
            off(8, 14000f),
        ),
    )

    private val DeEss = EQPreset(
        name = "De-Ess",
        description = "Narrow 6 kHz notch — sibilance reduction",
        category = PresetCategory.REPAIR,
        bands = listOf(
            off(1, 60f), off(2, 150f), off(3, 320f), off(4, 700f), off(5, 1500f),
            band(6, EQBandType.BELL, 6000f, -5f, 8f),
            band(7, EQBandType.BELL, 8500f, -3f, 6f),
            off(8, 14000f),
        ),
    )

    private val MasterBus = EQPreset(
        name = "Master Bus",
        description = "Gentle mastering polish — 200 Hz dip + high shelf",
        category = PresetCategory.MASTER,
        bands = listOf(
            off(1, 60f),
            band(2, EQBandType.BELL, 200f, -1f, 1.0f),
            off(3, 320f), off(4, 700f), off(5, 1500f),
            band(6, EQBandType.BELL, 3500f, 0.5f, 1.2f),
            band(7, EQBandType.HIGH_SHELF, 10000f, 1f, 0.5f),
            band(8, EQBandType.HIGH_SHELF, 16000f, 1f, 0.5f),
        ),
    )

    val ALL: List<EQPreset> = listOf(
        Flat, AirLift, VintageConsole, PultecSmooth, ApiPunch,
        MassiveLow, NeveWarmth, BroadcastVoice, DeEss, MasterBus,
    )
}
