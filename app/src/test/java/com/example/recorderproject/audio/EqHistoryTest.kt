package com.example.recorderproject.audio

import com.example.recorderproject.model.EQChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EqHistoryTest {

    // Three structurally-distinct chains (EQChain is a data class → value equality).
    private val a = EQChain.empty()
    private val b = EQChain.empty().copy(bypassed = true)
    private val c = EQChain.empty().copy(gainCompensation = true)

    @Test fun `undo returns the pushed pre-change state`() {
        val h = EqHistory()
        h.push(a)
        assertEquals(a, h.undo(b))
    }

    @Test fun `redo after undo restores the undone state`() {
        val h = EqHistory()
        h.push(a)
        val restored = h.undo(b)!!   // returns a; b moved to redo
        assertEquals(b, h.redo(restored))
    }

    @Test fun `a new push clears the redo stack`() {
        val h = EqHistory()
        h.push(a)
        h.undo(b)        // redo now holds b
        h.push(c)        // clears redo
        assertNull(h.redo(a))
    }

    @Test fun `undo on empty history returns null`() {
        assertNull(EqHistory().undo(a))
    }

    @Test fun `redo on empty stack returns null`() {
        assertNull(EqHistory().redo(a))
    }

    @Test fun `clear empties both stacks`() {
        val h = EqHistory()
        h.push(a)
        h.undo(b)          // undo stack now empty-ish, redo holds b
        h.clear()
        assertNull(h.undo(c))
        assertNull(h.redo(c))
    }

    @Test fun `cap keeps only the most recent states`() {
        val h = EqHistory(cap = 2)
        h.push(a); h.push(b); h.push(c)   // 'a' evicted; undo stack = [b, c]
        assertEquals(c, h.undo(EQChain.empty()))
        assertEquals(b, h.undo(EQChain.empty()))
        assertNull(h.undo(EQChain.empty()))
    }
}
