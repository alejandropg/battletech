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
        val content = TextCursor(canvas)
        for (child in children) {
            if (content.row >= canvas.height) break
            content.draw(child)
            repeat(gutter) { content.newLine() }
        }
    }
}
