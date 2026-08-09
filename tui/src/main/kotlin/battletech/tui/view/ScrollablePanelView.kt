package battletech.tui.view

import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color

internal class ScrollablePanelView(
    private val key: Char,
    private val title: String,
    private val content: View,
    private val scrollOffset: Int?,
    private val anchorBottom: Boolean = false,
) : View {

    var maxOffset: Int = 0
        private set

    override fun render(canvas: Canvas) {
        val inner = PanelChrome.draw(canvas, title, badge = key.toString())

        if (inner.width <= 0 || inner.height <= 0) {
            maxOffset = 0
            return
        }

        val scratch = Canvas.offscreen(inner.width, MAX_CONTENT_ROWS)
        content.render(scratch)

        val contentHeight = scratch.contentHeight()
        maxOffset = (contentHeight - inner.height).coerceAtLeast(0)

        val offset = (scrollOffset ?: if (anchorBottom) maxOffset else 0).coerceIn(0, maxOffset)

        inner.blit(scratch, 0, offset, 0, 0, inner.width, inner.height)

        val thumbRange = Scrollbar.thumb(
            track = inner.height,
            contentHeight = contentHeight,
            viewportHeight = inner.height,
            offset = offset,
        )
        if (thumbRange != null) {
            for (row in thumbRange) {
                canvas.set(canvas.width - 1, PanelChrome.CONTENT_INSET.top + row, Cell("▐", GREEN_STYLE))
            }
        }
    }

    private companion object {
        private const val MAX_CONTENT_ROWS = 512
        private val GREEN_STYLE = Cell.Style(Color.GREEN)
    }
}
