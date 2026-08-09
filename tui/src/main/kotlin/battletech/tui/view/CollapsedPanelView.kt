package battletech.tui.view

import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color

public class CollapsedPanelView(public val key: Char, public val title: String) : View {

    override fun render(canvas: Canvas) {
        canvas.drawBox()
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
