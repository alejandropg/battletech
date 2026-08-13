package tenter.view

import tenter.screen.Canvas
import tenter.screen.Insets

/** Decorates [content], rendering it into the region left after removing [insets]. */
public class Padded(private val insets: Insets, private val content: View) : View {
    override fun draw(canvas: Canvas) {
        content.draw(canvas.inset(insets))
    }
}
