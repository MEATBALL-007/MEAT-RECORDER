package com.example.recorderproject.model

/** One row in a workspace layout: a control and whether it is shown. */
data class WorkspaceItem(val control: QuickControl, val visible: Boolean)

/** Ordered, show/hide-able set of quick controls for the recording screen. */
data class WorkspaceLayout(val items: List<WorkspaceItem>) {
    /** Controls to render, in order, skipping hidden ones. */
    fun visibleControls(): List<QuickControl> = items.filter { it.visible }.map { it.control }

    companion object {
        /** Every control, in declaration order, all visible. */
        val DEFAULT = WorkspaceLayout(QuickControl.entries.map { WorkspaceItem(it, true) })
    }
}

fun WorkspaceLayout.moveUp(index: Int): WorkspaceLayout {
    if (index <= 0 || index >= items.size) return this
    val list = items.toMutableList()
    val tmp = list[index - 1]; list[index - 1] = list[index]; list[index] = tmp
    return WorkspaceLayout(list)
}

fun WorkspaceLayout.moveDown(index: Int): WorkspaceLayout {
    if (index < 0 || index >= items.lastIndex) return this
    val list = items.toMutableList()
    val tmp = list[index + 1]; list[index + 1] = list[index]; list[index] = tmp
    return WorkspaceLayout(list)
}

fun WorkspaceLayout.toggleVisible(index: Int): WorkspaceLayout {
    if (index < 0 || index >= items.size) return this
    val list = items.toMutableList()
    list[index] = list[index].copy(visible = !list[index].visible)
    return WorkspaceLayout(list)
}

/**
 * Reconciles a (possibly stale) layout with the current [QuickControl] registry:
 * drops items whose control no longer exists (handled at parse time) and appends
 * any registry controls missing from this layout, visible, at the end. Keeps
 * existing order/visibility for known controls.
 */
fun WorkspaceLayout.mergedWithDefaults(): WorkspaceLayout {
    val present = items.map { it.control }.toSet()
    val missing = QuickControl.entries.filter { it !in present }.map { WorkspaceItem(it, true) }
    return WorkspaceLayout(items + missing)
}
