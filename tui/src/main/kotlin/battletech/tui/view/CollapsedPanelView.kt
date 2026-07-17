package battletech.tui.view

import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public class CollapsedPanelView(public val index: Int, public val title: String) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height)
        buffer.writeString(x + 2, y, "[$index]", Cell.Style(Color.BRIGHT_YELLOW))

        val centerX = x + 1 + (width - 2) / 2
        val bottomBorderRow = y + height - 1

        for ((i, ch) in title.withIndex()) {
            val row = y + 1 + i
            if (row >= bottomBorderRow) break
            if (ch != ' ') {
                buffer.writeString(centerX, row, ch.toString(), Cell.Style(Color.BRIGHT_YELLOW))
            }
        }
    }
}
