package battletech.tui.view

/**
 * Pure arithmetic description of one rendered frame, derived solely from
 * terminal dimensions and visibility state — no UI, no rendering, no I/O.
 *
 * The tactical board fills the leftmost portion; side panels are placed to its
 * right in [slots] order (matching [Panels.ordered]).
 *
 * @param boardWidth  columns available for the hex board
 * @param boardHeight rows available for the hex board
 * @param slots       visible panel slots, left-to-right, each with its screen-x
 *                    position and allocated width
 */
internal data class PanelSlotLayout(
    /** Matches [battletech.tui.game.PanelId.index] of the corresponding panel. */
    val panelIndex: Int,
    /** Left edge (column) of this panel in screen coordinates. */
    val x: Int,
    /** Allocated width in columns; never zero (hidden panels are absent from the list). */
    val width: Int,
    /** True when the user has collapsed this panel to a narrow stub. */
    val collapsed: Boolean,
)

internal data class FrameLayout(
    val boardWidth: Int,
    val boardHeight: Int,
    val slots: List<PanelSlotLayout>,
) {
    companion object {
        /** Rows consumed by the status bar below the board and panels. */
        const val STATUS_BAR_HEIGHT: Int = 7

        /** Column width of a collapsed panel stub. */
        const val COLLAPSED_STUB_WIDTH: Int = 7

        /**
         * Computes the frame layout from terminal dimensions and panel visibility.
         *
         * @param termWidth       full terminal width in columns
         * @param termHeight      full terminal height in rows
         * @param visiblePanels   set of [battletech.tui.game.PanelId.index] values that
         *                        should appear this frame
         * @param collapsedPanels set of [battletech.tui.game.PanelId.index] values that
         *                        the user has collapsed to a narrow stub
         * @param panelDescriptors ordered list of (panelIndex, expandedWidth) pairs,
         *                         matching the left-to-right render order of [Panels.ordered]
         */
        fun compute(
            termWidth: Int,
            termHeight: Int,
            visiblePanels: Set<Int>,
            collapsedPanels: Set<Int>,
            panelDescriptors: List<Pair<Int, Int>>,
        ): FrameLayout {
            fun allocatedWidth(panelIndex: Int, expandedWidth: Int): Int = when {
                panelIndex !in visiblePanels -> 0
                panelIndex in collapsedPanels -> COLLAPSED_STUB_WIDTH
                else -> expandedWidth
            }

            val totalPanelWidth = panelDescriptors.sumOf { (idx, w) -> allocatedWidth(idx, w) }
            val boardWidth = termWidth - totalPanelWidth
            val boardHeight = termHeight - STATUS_BAR_HEIGHT

            val slots = buildList {
                var nextX = boardWidth
                for ((idx, expandedWidth) in panelDescriptors) {
                    val width = allocatedWidth(idx, expandedWidth)
                    if (width <= 0) continue
                    add(PanelSlotLayout(
                        panelIndex = idx,
                        x = nextX,
                        width = width,
                        collapsed = idx in collapsedPanels,
                    ))
                    nextX += width
                }
            }

            return FrameLayout(boardWidth, boardHeight, slots)
        }
    }
}
