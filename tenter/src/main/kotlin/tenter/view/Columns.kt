package tenter.view

import tenter.screen.Canvas

/**
 * Packs [children] left-to-right across the canvas, [gutter]-separated, wrapping to a new band
 * beneath the tallest child in the current band whenever the next child would not fit. tenter
 * has no other sibling-composition decorator — most content just stacks vertically through a
 * single [TextCursor] — but a wide graphical layout (cards side by side in a record sheet) needs
 * to degrade to a narrower terminal instead of clipping, which is what this buys.
 *
 * Each child is measured by drawing it once into its own [Canvas.offscreen] and reading
 * [Canvas.contentHeight] — the same measure-then-place approach [Viewport] uses for
 * [ContentExtent.Measured] content — then [Canvas.blit] places the result. A child wider than
 * the canvas is placed alone on its own band and clipped by [Canvas.blit], never widened.
 */
public class Columns(
    private val children: List<Child>,
    private val gutter: Int = 2,
) : View {

    public data class Child(val width: Int, val view: View)

    override fun draw(canvas: Canvas) {
        var x = 0
        var bandTop = 0
        var bandHeight = 0

        for (child in children) {
            val gutterBefore = if (x == 0) 0 else gutter
            if (x != 0 && x + gutterBefore + child.width > canvas.width) {
                bandTop += bandHeight + 1
                bandHeight = 0
                x = 0
            }
            val childX = if (x == 0) 0 else x + gutter

            val stream = Canvas.offscreen(child.width, canvas.height)
            child.view.draw(stream)
            val height = stream.contentHeight()
            canvas.blit(stream, 0, 0, childX, bandTop, child.width, height)

            bandHeight = maxOf(bandHeight, height)
            x = childX + child.width
        }
    }
}
