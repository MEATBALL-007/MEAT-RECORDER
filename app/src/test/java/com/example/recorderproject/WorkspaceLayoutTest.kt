package com.example.recorderproject

import com.example.recorderproject.model.QuickControl
import com.example.recorderproject.model.WorkspaceLayout
import com.example.recorderproject.model.WorkspaceItem
import com.example.recorderproject.model.moveUp
import com.example.recorderproject.model.moveDown
import com.example.recorderproject.model.toggleVisible
import com.example.recorderproject.model.mergedWithDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLayoutTest {
    @Test fun default_has_all_controls_visible_in_declaration_order() {
        val d = WorkspaceLayout.DEFAULT
        assertEquals(QuickControl.entries.toList(), d.items.map { it.control })
        assertTrue(d.items.all { it.visible })
    }

    @Test fun visibleControls_filters_hidden() {
        val l = WorkspaceLayout.DEFAULT.toggleVisible(0) // hide first
        assertTrue(QuickControl.entries[0] !in l.visibleControls())
        assertEquals(QuickControl.entries.size - 1, l.visibleControls().size)
    }

    @Test fun moveDown_then_moveUp_is_identity() {
        val start = WorkspaceLayout.DEFAULT
        val moved = start.moveDown(0).moveUp(1)
        assertEquals(start.items, moved.items)
    }

    @Test fun moveUp_at_top_is_noop() {
        val start = WorkspaceLayout.DEFAULT
        assertEquals(start.items, start.moveUp(0).items)
    }

    @Test fun moveDown_at_bottom_is_noop() {
        val start = WorkspaceLayout.DEFAULT
        val last = start.items.lastIndex
        assertEquals(start.items, start.moveDown(last).items)
    }

    @Test fun toggleVisible_flips_one_item() {
        val l = WorkspaceLayout.DEFAULT.toggleVisible(2)
        assertEquals(false, l.items[2].visible)
        assertEquals(true, l.toggleVisible(2).items[2].visible)
    }

    @Test fun mergedWithDefaults_appends_missing_and_drops_unknown() {
        val partial = WorkspaceLayout(
            WorkspaceLayout.DEFAULT.items.filter { it.control != QuickControl.VAD }
        )
        val merged = partial.mergedWithDefaults()
        assertEquals(QuickControl.entries.size, merged.items.size)
        assertEquals(QuickControl.VAD, merged.items.last().control)
        assertTrue(merged.items.last().visible)
    }
}
