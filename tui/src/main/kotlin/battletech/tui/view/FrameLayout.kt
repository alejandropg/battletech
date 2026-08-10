package battletech.tui.view

import battletech.tui.game.PanelId

internal data class FrameLayout(
    val boardWidth: Int,
    val boardHeight: Int,
    val boardY: Int,
    val slots: List<PlacedPanel>,
) {
    /**
     * Returns the expanded (non-collapsed) [PlacedPanel] that contains
     * screen column [x] at screen row [y], or `null` if none matches.
     *
     * Only rows `boardY until boardY + boardHeight` are considered; clicks on
     * the status bar, board area, or collapsed stubs return null.
     */
    fun slotAt(x: Int, y: Int): PlacedPanel? {
        if (y < boardY || y >= boardY + boardHeight) return null
        return slots.firstOrNull { slot -> !slot.collapsed && x >= slot.x && x < slot.x + slot.width }
    }

    internal companion object {
        /** Rows consumed by the status bar above the board and panels. */
        const val STATUS_BAR_HEIGHT: Int = 4

        /** Column width of a collapsed panel stub. */
        const val COLLAPSED_STUB_WIDTH: Int = 7

        /**
         * Computes the frame layout from terminal dimensions and panel visibility.
         *
         * @param termWidth       full terminal width in columns
         * @param termHeight      full terminal height in rows
         * @param visiblePanels   [PanelId]s that should appear this frame
         * @param collapsedPanels [PanelId]s the user has collapsed
         * @param panels          the registry, in left-to-right render order (matches [Panels.ordered])
         */
        fun compute(
            termWidth: Int,
            termHeight: Int,
            visiblePanels: Set<PanelId>,
            collapsedPanels: Set<PanelId>,
            panels: List<Panel>,
        ): FrameLayout {
            fun allocatedWidth(panel: Panel): Int = when (panel.id) {
                !in visiblePanels -> 0
                in collapsedPanels -> panel.collapsedWidth
                else -> panel.width
            }

            val totalPanelWidth = panels.sumOf(::allocatedWidth)
            val boardWidth = termWidth - totalPanelWidth
            val boardHeight = termHeight - STATUS_BAR_HEIGHT
            val boardY = STATUS_BAR_HEIGHT

            val slots = buildList {
                var nextX = boardWidth
                for (panel in panels) {
                    val width = allocatedWidth(panel)
                    if (width <= 0) continue
                    add(PlacedPanel(
                        panel = panel,
                        x = nextX,
                        width = width,
                        collapsed = panel.id in collapsedPanels,
                    ))
                    nextX += width
                }
            }

            return FrameLayout(boardWidth, boardHeight, boardY, slots)
        }
    }
}
