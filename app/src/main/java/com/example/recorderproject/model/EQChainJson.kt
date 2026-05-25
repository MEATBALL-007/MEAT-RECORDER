package com.example.recorderproject.model

import org.json.JSONArray
import org.json.JSONObject

object EQChainJson {

    private const val SCHEMA_VERSION = 1

    fun toJsonString(chain: EQChain): String {
        val root = JSONObject()
        root.put("schema", SCHEMA_VERSION)
        root.put("bypassed", chain.bypassed)
        root.put("gainCompensation", chain.gainCompensation)
        val arr = JSONArray()
        for (b in chain.bands) {
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("type", b.type.name)
                put("frequencyHz", b.frequencyHz.toDouble())
                put("gainDb", b.gainDb.toDouble())
                put("q", b.q.toDouble())
                put("enabled", b.enabled)
                put("soloed", b.soloed)
                put("muted", b.muted)
                put("locked", b.locked)
            })
        }
        root.put("bands", arr)
        return root.toString()
    }

    /** Returns null if JSON is malformed or required fields are missing. */
    fun fromJsonString(json: String): EQChain? = try {
        val root = JSONObject(json)
        val arr = root.getJSONArray("bands")
        val bands = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            EQBand(
                id = o.getInt("id"),
                type = EQBandType.valueOf(o.getString("type")),
                frequencyHz = o.getDouble("frequencyHz").toFloat(),
                gainDb = o.getDouble("gainDb").toFloat(),
                q = o.getDouble("q").toFloat(),
                enabled = o.optBoolean("enabled", true),
                soloed = o.optBoolean("soloed", false),
                muted = o.optBoolean("muted", false),
                locked = o.optBoolean("locked", false),
            )
        }
        EQChain(
            bands = bands,
            bypassed = root.optBoolean("bypassed", false),
            gainCompensation = root.optBoolean("gainCompensation", false),
        )
    } catch (e: Exception) {
        null
    }
}
