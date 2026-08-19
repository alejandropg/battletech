package tenter.panel

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.View

/**
 * [title], one letter per row, centered in whatever space it is given — content only, no border.
 * A minimized panel's stub content; [Panel.render] wraps it (and every other state) in its own
 * chrome, so this view never draws a border of its own — see `Bordered`'s KDoc.
 */
public class VerticalTitleView(private val title: String) : View {
    override fun draw(canvas: Canvas) {
        val centerX = canvas.width / 2
        for ((row, ch) in title.withIndex()) {
            if (row >= canvas.height) break
            if (ch != ' ') canvas.writeString(centerX, row, ch.toString(), TEXT_PRIMARY_STYLE)
        }
    }

    private companion object {
        private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    }
}
