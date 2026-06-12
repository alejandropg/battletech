package battletech.tui.game

import battletech.tui.view.FrameLayout
import battletech.tui.view.PanelSlotLayout

/**
 * Pure scroll-offset update logic for side panels.
 *
 * Scroll state is kept in [AppState.panelScrollOffsets] as a minimal map:
 * **absent = anchored** (0 for top-anchored, maxOffset for bottom-anchored panels).
 * When a wheel update would land back on the anchor value the entry is removed,
 * which naturally implements LOG's re-stick behaviour.
 */
internal object PanelScroll {

    /** Number of rows scrolled per wheel tick (matches lazygit default). */
    const val STEP: Int = 2

    /**
     * Returns a new offsets map after applying [delta] to the panel at [panelIndex].
     *
     * - Clamps the result to `0..maxOffset`.
     * - Removes the entry when the new offset equals the anchor value
     *   (0 for top-anchored, [maxOffset] for bottom-anchored).
     * - When [maxOffset] <= 0 cleans any stale entry for [panelIndex] and
     *   returns without further mutation.
     */
    fun update(
        offsets: Map<Int, Int>,
        panelIndex: Int,
        delta: Int,
        maxOffset: Int,
        anchorBottom: Boolean,
    ): Map<Int, Int> {
        if (maxOffset <= 0) {
            return if (panelIndex in offsets) offsets - panelIndex else offsets
        }
        val anchorValue = if (anchorBottom) maxOffset else 0
        val current = offsets[panelIndex] ?: anchorValue
        val next = (current + delta).coerceIn(0, maxOffset)
        return if (next == anchorValue) {
            offsets - panelIndex
        } else {
            offsets + (panelIndex to next)
        }
    }

    /**
     * Returns the expanded (non-collapsed) [PanelSlotLayout] that contains
     * screen column [x] at screen row [y], or `null` if none matches.
     *
     * Only rows `0 until layout.boardHeight` are considered; clicks on the
     * status bar, board area, or collapsed stubs return null.
     */
    fun slotAt(layout: FrameLayout, x: Int, y: Int): PanelSlotLayout? {
        if (y < 0 || y >= layout.boardHeight) return null
        return layout.slots.firstOrNull { slot ->
            !slot.collapsed && x >= slot.x && x < slot.x + slot.width
        }
    }
}
