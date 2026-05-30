package com.example.recorderproject.model

import org.json.JSONObject

/**
 * Outcome of a single offline delivery render.
 */
data class DeliveryResult(
    val integratedLufs: Float,
    val shortTermMaxLufs: Float,
    val momentaryMaxLufs: Float,
    val truePeakDbtp: Float,
    val lra: Float,
    val appliedGainDb: Float,
    val targetLufs: Float?,
    val tpCeilingDbtp: Float?,
    val passed: Boolean,
    val deliveryFile: String,
    val renderedAt: Long,
) {
    companion object {
        fun toJson(r: DeliveryResult): String = JSONObject().apply {
            put("integratedLufs",    r.integratedLufs.toDouble())
            put("shortTermMaxLufs",  r.shortTermMaxLufs.toDouble())
            put("momentaryMaxLufs",  r.momentaryMaxLufs.toDouble())
            put("truePeakDbtp",      r.truePeakDbtp.toDouble())
            put("lra",               r.lra.toDouble())
            put("appliedGainDb",     r.appliedGainDb.toDouble())
            put("targetLufs",        r.targetLufs?.toDouble() ?: JSONObject.NULL)
            put("tpCeilingDbtp",     r.tpCeilingDbtp?.toDouble() ?: JSONObject.NULL)
            put("passed",            r.passed)
            put("deliveryFile",      r.deliveryFile)
            put("renderedAt",        r.renderedAt)
        }.toString()

        fun fromJson(s: String): DeliveryResult {
            val j = JSONObject(s)
            return DeliveryResult(
                integratedLufs   = j.getDouble("integratedLufs").toFloat(),
                shortTermMaxLufs = j.getDouble("shortTermMaxLufs").toFloat(),
                momentaryMaxLufs = j.getDouble("momentaryMaxLufs").toFloat(),
                truePeakDbtp     = j.getDouble("truePeakDbtp").toFloat(),
                lra              = j.getDouble("lra").toFloat(),
                appliedGainDb    = j.getDouble("appliedGainDb").toFloat(),
                targetLufs       = if (j.isNull("targetLufs")) null else j.getDouble("targetLufs").toFloat(),
                tpCeilingDbtp    = if (j.isNull("tpCeilingDbtp")) null else j.getDouble("tpCeilingDbtp").toFloat(),
                passed           = j.getBoolean("passed"),
                deliveryFile     = j.getString("deliveryFile"),
                renderedAt       = j.getLong("renderedAt"),
            )
        }
    }
}
