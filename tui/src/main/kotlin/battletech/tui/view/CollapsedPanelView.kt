package battletech.tui.view

import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color

/** A panel shrunk to its stub width: a plain box carrying only the `[key]` badge and the panel's title, one letter per row. */
internal class CollapsedPanelView(val key: Char, val title: String) : View {

    private val bordered = Bordered(title = "", badge = key.toString(), content = VerticalTitle(title))

    override fun render(canvas: Canvas) = bordered.render(canvas)

    /** [title], one letter per row, centered in whatever space [Bordered] gives it. */
    private class VerticalTitle(private val title: String) : View {
        override fun render(canvas: Canvas) {
            val centerX = canvas.width / 2
            for ((row, ch) in title.withIndex()) {
                if (row >= canvas.height) break
                if (ch != ' ') canvas.writeString(centerX, row, ch.toString(), BRIGHT_YELLOW_STYLE)
            }
        }
    }

    private companion object {
        private val BRIGHT_YELLOW_STYLE = Cell.Style(Color.BRIGHT_YELLOW)
    }
}
