package com.example.recorderproject.model

import org.json.JSONArray
import org.json.JSONObject

/** Wrapper overload used by tests/callers that prefer function-call form. */
fun moveDown(layout: WorkspaceLayout, index: Int): WorkspaceLayout = layout.moveDown(index)

/**
 * JSON persistence for [WorkspaceLayout], mirroring EQChainJson: org.json,
 * schema-versioned, null on malformed input. On load, unknown control ids are
 * skipped and any registry controls missing from the file are appended via
 * [mergedWithDefaults].
 */
object WorkspaceLayoutJson {
    private const val SCHEMA_VERSION = 1

    fun toJsonString(layout: WorkspaceLayout): String {
        val root = JSONObject()
        root.put("schema", SCHEMA_VERSION)
        val arr = JSONArray()
        for (item in layout.items) {
            arr.put(JSONObject().apply {
                put("id", item.control.id)
                put("visible", item.visible)
            })
        }
        root.put("items", arr)
        return root.toString()
    }

    fun fromJsonString(json: String): WorkspaceLayout? = try {
        val root = JSONObject(json)
        val arr = root.getJSONArray("items")
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val control = QuickControl.fromId(o.getString("id")) ?: return@mapNotNull null
            WorkspaceItem(control, o.optBoolean("visible", true))
        }
        WorkspaceLayout(items).mergedWithDefaults()
    } catch (e: Exception) {
        null
    }
}
