package battletech.tui.view

import battletech.tui.game.PanelId

internal data class PanelMetrics(val key: PanelId, val expandedWidth: Int, val collapsedWidth: Int)

internal data class FrameLayout(
    val boardWidth: Int,
    val boardHeight: Int,
    val boardY: Int,
    val slots: List<PanelSlotLayout>,
) {
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
         * @param panelDescriptors ordered [PanelMetrics], matching the left-to-right
         *                         render order of [Panels.ordered]
         */
        fun compute(
            termWidth: Int,
            termHeight: Int,
            visiblePanels: Set<PanelId>,
            collapsedPanels: Set<PanelId>,
            panelDescriptors: List<PanelMetrics>,
        ): FrameLayout {
            fun allocatedWidth(panel: PanelMetrics): Int = when (panel.key) {
                !in visiblePanels -> 0
                in collapsedPanels -> panel.collapsedWidth
                else -> panel.expandedWidth
            }

            val totalPanelWidth = panelDescriptors.sumOf(::allocatedWidth)
            val boardWidth = termWidth - totalPanelWidth
            val boardHeight = termHeight - STATUS_BAR_HEIGHT
            val boardY = STATUS_BAR_HEIGHT

            val slots = buildList {
                var nextX = boardWidth
                for (panel in panelDescriptors) {
                    val width = allocatedWidth(panel)
                    if (width <= 0) continue
                    add(PanelSlotLayout(
                        panelKey = panel.key,
                        x = nextX,
                        width = width,
                        collapsed = panel.key in collapsedPanels,
                    ))
                    nextX += width
                }
            }

            return FrameLayout(boardWidth, boardHeight, boardY, slots)
        }
    }
}
