package com.example.recorderproject.model

data class EQChain(
    val bands: List<EQBand>,
    val noiseCutSuggestions: List<EQBand> = emptyList(),
    val bypassed: Boolean = false,
    val gainCompensation: Boolean = false,
) {
    companion object {
        /** A chain of 8 disabled default bands — Flat starting state. */
        fun empty(): EQChain = EQChain(
            bands = (1..EQBand.MAX_BANDS).map { EQBand.defaultForSlot(it) }
        )
    }

    /** Replace a band by id, returns new chain. */
    fun withBand(updated: EQBand): EQChain =
        copy(bands = bands.map { if (it.id == updated.id) updated else it })

    /** Add a band, capped at MAX_BANDS. Returns null if full. */
    fun withAddedBand(band: EQBand): EQChain? {
        if (bands.count { it.enabled } >= EQBand.MAX_BANDS) return null
        val firstDisabledIdx = bands.indexOfFirst { !it.enabled }
        if (firstDisabledIdx < 0) return null
        val rebuilt = bands.toMutableList()
        rebuilt[firstDisabledIdx] = band.copy(id = bands[firstDisabledIdx].id, enabled = true)
        return copy(bands = rebuilt)
    }
}
