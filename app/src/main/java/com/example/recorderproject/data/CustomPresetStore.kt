package com.example.recorderproject.data

import android.content.Context
import com.example.recorderproject.model.EQChain
import com.example.recorderproject.model.EQChainJson
import com.example.recorderproject.model.EQPreset
import com.example.recorderproject.model.PresetCategory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * User-saved presets live as JSON files under <filesDir>/presets/.
 * Phase 2 feature; ships alongside the built-in EQPresets.ALL list.
 */
class CustomPresetStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "presets").apply { mkdirs() }

    fun save(name: String, chain: EQChain, description: String = "User preset", category: PresetCategory = PresetCategory.NEUTRAL) {
        val safeName = name.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "$safeName.json")
        val root = JSONObject()
        root.put("name", name)
        root.put("description", description)
        root.put("category", category.name)
        root.put("chain", JSONObject(EQChainJson.toJsonString(chain)))
        file.writeText(root.toString())
    }

    fun listAll(): List<EQPreset> {
        val files = dir.listFiles { f -> f.extension == "json" }?.toList() ?: emptyList()
        return files.mapNotNull { parse(it) }
    }

    fun delete(name: String): Boolean {
        val safeName = name.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir, "$safeName.json").delete()
    }

    private fun parse(file: File): EQPreset? = try {
        val root = JSONObject(file.readText())
        val chain = EQChainJson.fromJsonString(root.getJSONObject("chain").toString()) ?: return null
        EQPreset(
            name = root.getString("name"),
            description = root.optString("description", ""),
            category = runCatching { PresetCategory.valueOf(root.optString("category", PresetCategory.NEUTRAL.name)) }
                .getOrDefault(PresetCategory.NEUTRAL),
            bands = chain.bands,
        )
    } catch (e: Exception) {
        null
    }
}
