package battletech.tui.view

import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.Insets

internal class CollapsedPanelView(val key: Char, val title: String) : Pane {

    override val scroll: ScrollState = ScrollState.NONE

    override fun render(canvas: Canvas) {
        PanelChrome.draw(canvas, padding = Insets.NONE)
        canvas.writeString(2, 0, "[$key]", BRIGHT_YELLOW_STYLE)

        val centerX = 1 + (canvas.width - 2) / 2
        val bottomBorderRow = canvas.height - 1

        for ((i, ch) in title.withIndex()) {
            val row = 1 + i
            if (row >= bottomBorderRow) break
            if (ch != ' ') {
                canvas.writeString(centerX, row, ch.toString(), BRIGHT_YELLOW_STYLE)
            }
        }
    }

    private companion object {
        private val BRIGHT_YELLOW_STYLE = Cell.Style(Color.BRIGHT_YELLOW)
    }
}
