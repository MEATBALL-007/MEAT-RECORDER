package com.example.recorderproject.audio

import com.example.recorderproject.model.EQChain

/**
 * Undo/redo stacks for an EQChain editor. Pure (no Android types) → unit-testable.
 * Mirrors the original ViewModel mechanics: [push] records the pre-change state and clears
 * redo; [undo]/[redo] move the "current" state between the two stacks. Capped at [cap].
 */
class EqHistory(private val cap: Int = 10) {
    init { require(cap >= 1) { "cap must be >= 1, was $cap" } }

    private val undoStack = ArrayDeque<EQChain>()
    // Redo is intentionally uncapped — it only ever grows from undone items, so it is
    // self-bounded by the undo stack. (Mirrors the original ViewModel, which also left redo uncapped.)
    private val redoStack = ArrayDeque<EQChain>()

    /** Record [current] as a pre-change checkpoint; clears redo; evicts oldest past [cap]. */
    fun push(current: EQChain) {
        undoStack.addLast(current)
        if (undoStack.size > cap) undoStack.removeFirst()
        redoStack.clear()
    }

    /** Return the state to restore (or null if none); pushes [current] onto the redo stack. */
    fun undo(current: EQChain): EQChain? {
        val prev = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return prev
    }

    /** Return the state to restore (or null if none); pushes [current] onto the undo stack. */
    fun redo(current: EQChain): EQChain? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
