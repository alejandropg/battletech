package battletech.tui.view

import battletech.tui.screen.Canvas
import battletech.tui.screen.Insets

/** Decorates [content], rendering it into the region left after removing [insets]. */
internal class Padded(private val insets: Insets, private val content: View) : View {
    override fun render(canvas: Canvas) {
        content.render(canvas.inset(insets))
    }
}
