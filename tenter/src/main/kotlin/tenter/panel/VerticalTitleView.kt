package tenter.panel

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.drawVerticalText
import tenter.view.View

/**
 * [title], one letter per row, centered in whatever space it is given — content only, no border.
 * A minimized panel's stub content; [Panel.render] wraps it (and every other state) in its own
 * chrome, so this view never draws a border of its own — see `Bordered`'s KDoc.
 */
public class VerticalTitleView(private val title: String) : View {
    override fun draw(canvas: Canvas) {
        drawVerticalText(canvas, title, TEXT_PRIMARY_STYLE)
    }

    private companion object {
        private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    }
}
