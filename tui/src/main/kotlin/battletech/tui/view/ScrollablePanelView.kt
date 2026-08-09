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
        val viewport = PanelChrome.drawScrollable(canvas, title, badge = key.toString())

        if (viewport.width <= 0 || viewport.height <= 0) {
            maxOffset = 0
            return
        }

        // The top padding is the stream's first row rather than a fixed viewport inset, so
        // it's visible at rest and the content reclaims its row as soon as the panel scrolls.
        val stream = Canvas.offscreen(viewport.width, MAX_CONTENT_ROWS)
        content.render(stream.inset(PanelChrome.PADDING.vertical()))

        val streamHeight = stream.contentHeight()
        maxOffset = (streamHeight - viewport.height).coerceAtLeast(0)

        val offset = (scrollOffset ?: if (anchorBottom) maxOffset else 0).coerceIn(0, maxOffset)

        viewport.blit(stream, 0, offset, 0, 0, viewport.width, viewport.height)

        val thumbRange = Scrollbar.thumb(
            track = viewport.height,
            contentHeight = streamHeight,
            viewportHeight = viewport.height,
            offset = offset,
        )
        if (thumbRange != null) {
            for (row in thumbRange) {
                canvas.set(canvas.width - 1, PanelChrome.VIEWPORT_INSET.top + row, Cell("▐", GREEN_STYLE))
            }
        }
    }

    private companion object {
        private const val MAX_CONTENT_ROWS = 512
        private val GREEN_STYLE = Cell.Style(Color.GREEN)
    }
}
