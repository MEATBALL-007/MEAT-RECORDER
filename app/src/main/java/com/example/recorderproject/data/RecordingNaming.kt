package com.example.recorderproject.data

/**
 * Pure naming/filtering rules for the recording library (the `_nr` noise-reduction twin,
 * `_delivery` siblings, `_eq.json`/`_delivery.json` sidecars, orphan-delivery cleanup).
 * No Android types, so it is fully unit-testable. Used by RecordingScanner.
 */
object RecordingNaming {
    private const val NR_SUFFIX = "_nr"
    private const val DELIVERY_SUFFIX = "_delivery"

    /** Name without its extension. */
    fun baseOf(name: String): String =
        if (name.contains('.')) name.substringBeforeLast('.') else name

    /** A noise-reduced file (base ends with `_nr`, case-insensitive). */
    fun hasNr(base: String): Boolean = base.lowercase().endsWith(NR_SUFFIX)

    /** A delivery render sibling (base ends with `_delivery`). Case-sensitive — the on-disk convention is always lowercase. */
    fun isDeliverySibling(base: String): Boolean = base.endsWith(DELIVERY_SUFFIX)

    /** Original base names that have an `_nr` twin — these originals are hidden in favor of the twin. */
    fun nrShadowedBases(wavBaseNames: Collection<String>): Set<String> =
        wavBaseNames.filter { it.lowercase().endsWith(NR_SUFFIX) }
            .map { it.dropLast(NR_SUFFIX.length) }
            .toSet()

    /** Whether a wav (by base name) should be hidden from the library list. */
    fun isHidden(base: String, nrShadowed: Set<String>): Boolean =
        isDeliverySibling(base) || (!hasNr(base) && nrShadowed.contains(base))

    /** Sidecar/companion file names for a given recording base. */
    fun eqSidecarName(base: String): String = "${base}_eq.json"
    fun deliveryWavName(base: String): String = "${base}_delivery.wav"
    fun deliveryJsonName(base: String): String = "${base}_delivery.json"

    /** A `_delivery.wav` whose original is gone and which has no sidecar JSON — safe to delete. */
    fun isOrphanDelivery(base: String, originalBases: Set<String>, hasSidecarJson: Boolean): Boolean {
        if (!isDeliverySibling(base)) return false
        val originalBase = base.removeSuffix(DELIVERY_SUFFIX)
        return originalBase !in originalBases && !hasSidecarJson
    }
}
