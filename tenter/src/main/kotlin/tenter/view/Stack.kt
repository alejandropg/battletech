package tenter.view

import tenter.screen.Canvas

/**
 * Draws [children] one below another, top to bottom, [gutter] blank rows apart — each measured
 * by its own content height (the same measure-then-place approach [Columns] uses horizontally),
 * so a caller can compose an unknown-height view atop another without knowing either's row count
 * up front. A child that would start at or past the bottom of the canvas is skipped rather than
 * drawn off-canvas; a child that doesn't fully fit in what's left is clipped, never stretched.
 */
public class Stack(
    private val children: List<View>,
    private val gutter: Int = 1,
) : View {

    override fun draw(canvas: Canvas) {
        var y = 0
        for (child in children) {
            if (y >= canvas.height) break
            val remaining = canvas.region(0, y, canvas.width, canvas.height - y)
            val stream = Canvas.offscreen(remaining.width, remaining.height)
            child.draw(stream)
            val height = stream.contentHeight()
            canvas.blit(stream, 0, 0, 0, y, remaining.width, height)
            y += height + gutter
        }
    }
}
