package tenter.view

import tenter.screen.Canvas
import tenter.screen.Cell

internal class CanvasTextSink(private val canvas: Canvas) : TextSink {
    override val width: Int get() = canvas.width

    override fun write(column: Int, row: Int, text: String, style: Cell.Style) {
        canvas.writeString(column, row, text, style)
    }

    override fun reveal(x: Int, y: Int, width: Int, height: Int) {
        canvas.markReveal(x, y, width, height)
    }

    override fun place(layout: ContentLayout, x: Int, y: Int) {
        canvas.clearReveal()
        if (layout.width == 0 || layout.height == 0) return
        val stream = Canvas.offscreen(layout.width, layout.height)
        layout.draw(stream)
        canvas.blit(stream, 0, 0, x, y, layout.width, layout.height)
        stream.revealRect()?.let { reveal ->
            canvas.markReveal(x + reveal.x, y + reveal.y, reveal.width, reveal.height)
        }
    }

    internal fun drawLegacy(view: View, row: Int): Int {
        val remaining = canvas.region(0, row, canvas.width, canvas.height - row)
        if (remaining.width <= 0 || remaining.height <= 0) return 0
        val stream = Canvas.offscreen(remaining.width, remaining.height)
        view.draw(stream)
        val used = stream.contentHeight()
        canvas.blit(stream, 0, 0, 0, row, remaining.width, used)
        stream.revealRect()?.let { reveal ->
            canvas.markReveal(reveal.x, row + reveal.y, reveal.width, reveal.height)
        }
        return used
    }
}
