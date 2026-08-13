package tenter.panel

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.UiRole
import tenter.view.Bordered
import tenter.view.View

/** A panel shrunk to its stub width: a plain box carrying only the `[badge]` and the panel's title, one letter per row. */
public class CollapsedPanelView(public val badge: Char, public val title: String) : View {

    private val bordered = Bordered(title = "", badge = badge.toString(), content = VerticalTitle(title))

    override fun render(canvas: Canvas): Unit = bordered.render(canvas)

    /** [title], one letter per row, centered in whatever space [Bordered] gives it. */
    private class VerticalTitle(private val title: String) : View {
        override fun render(canvas: Canvas) {
            val centerX = canvas.width / 2
            for ((row, ch) in title.withIndex()) {
                if (row >= canvas.height) break
                if (ch != ' ') canvas.writeString(centerX, row, ch.toString(), ACCENT_STYLE)
            }
        }
    }

    private companion object {
        private val ACCENT_STYLE = Cell.Style(UiRole.ACCENT)
    }
}
