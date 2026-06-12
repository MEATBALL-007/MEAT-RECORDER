package com.example.recorderproject

import com.example.recorderproject.model.QuickControl
import com.example.recorderproject.model.WorkspaceLayout
import com.example.recorderproject.model.WorkspaceLayoutJson
import com.example.recorderproject.model.toggleVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceLayoutJsonTest {
    @Test fun round_trips_a_custom_layout() {
        val original = WorkspaceLayout.DEFAULT.toggleVisible(1).moveDownCompat(0)
        val json = WorkspaceLayoutJson.toJsonString(original)
        val parsed = WorkspaceLayoutJson.fromJsonString(json)
        assertEquals(original.items, parsed!!.items)
    }

    @Test fun bad_json_returns_null() {
        assertNull(WorkspaceLayoutJson.fromJsonString("not json"))
    }

    @Test fun unknown_ids_are_skipped_and_missing_appended() {
        val json = """{"schema":1,"items":[{"id":"vad","visible":false},{"id":"bogus","visible":true}]}"""
        val parsed = WorkspaceLayoutJson.fromJsonString(json)!!
        assertEquals(QuickControl.VAD, parsed.items.first().control)
        assertEquals(false, parsed.items.first().visible)
        assertEquals(QuickControl.entries.size, parsed.items.size) // bogus dropped, rest merged
    }
}

// Helper to keep the test independent of import ordering.
private fun WorkspaceLayout.moveDownCompat(i: Int) =
    com.example.recorderproject.model.moveDown(this, i)
