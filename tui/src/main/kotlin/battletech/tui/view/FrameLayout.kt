package battletech.tui.view

internal data class FrameLayout(
    val boardWidth: Int,
    val boardHeight: Int,
    val boardY: Int,
    val slots: List<PanelSlotLayout>,
) {
    internal companion object {
        /** Rows consumed by the status bar above the board and panels. */
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
            fun allocatedWidth(panelIndex: Int, expandedWidth: Int): Int = when (panelIndex) {
                !in visiblePanels -> 0
                in collapsedPanels -> COLLAPSED_STUB_WIDTH
                else -> expandedWidth
            }

            val totalPanelWidth = panelDescriptors.sumOf { (idx, w) -> allocatedWidth(idx, w) }
            val boardWidth = termWidth - totalPanelWidth
            val boardHeight = termHeight - STATUS_BAR_HEIGHT
            val boardY = STATUS_BAR_HEIGHT

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

            return FrameLayout(boardWidth, boardHeight, boardY, slots)
        }
    }
}
