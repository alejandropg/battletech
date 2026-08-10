package battletech.tui.game

import battletech.tui.view.FrameLayout
import battletech.tui.view.PanelSlotLayout

/**
 * Pure scroll-offset update logic for side panels.
 *
 * Scroll state is kept in [AppState.panelScrollOffsets] as a minimal map:
 * **absent = anchored at the top**. When a wheel update would land back at the top the entry
 * is removed.
 */
internal object PanelScroll {

    /** Number of rows scrolled per wheel tick (matches lazygit default). */
    const val STEP: Int = 2

    /**
     * Returns a new offsets map after applying [delta] to the panel at [panelKey], starting from
     * [currentOffset] — the panel's actually-visible offset as of the last render, which the
     * caller reads back from [battletech.tui.view.ScrollableView.state] rather than from this map.
     * That indirection matters once a panel auto-follows (see [battletech.tui.view.ScrollableView]'s
     * KDoc): a followed panel's true on-screen offset can be well away from its stale
     * "absent = anchored" entry here, and basing a wheel delta on the stale entry would jump the
     * panel instead of nudging it.
     *
     * - Clamps the result to `0..maxOffset`.
     * - Removes the entry when the new offset is `0` (the top).
     * - When [maxOffset] <= 0 cleans any stale entry for [panelKey] and
     *   returns without further mutation.
     */
    fun update(
        offsets: Map<PanelId, Int>,
        panelKey: PanelId,
        delta: Int,
        currentOffset: Int,
        maxOffset: Int,
    ): Map<PanelId, Int> {
        if (maxOffset <= 0) {
            return if (panelKey in offsets) offsets - panelKey else offsets
        }
        val next = (currentOffset + delta).coerceIn(0, maxOffset)
        return if (next == 0) {
            offsets - panelKey
        } else {
            offsets + (panelKey to next)
        }
    }

    /**
     * Returns the expanded (non-collapsed) [PanelSlotLayout] that contains
     * screen column [x] at screen row [y], or `null` if none matches.
     *
     * Only rows `layout.boardY until layout.boardY + layout.boardHeight` are
     * considered; clicks on the status bar, board area, or collapsed stubs
     * return null.
     */
    fun slotAt(layout: FrameLayout, x: Int, y: Int): PanelSlotLayout? {
        if (y < layout.boardY || y >= layout.boardY + layout.boardHeight) return null
        return layout.slots.firstOrNull { slot ->
            !slot.collapsed && x >= slot.x && x < slot.x + slot.width
        }
    }
}
