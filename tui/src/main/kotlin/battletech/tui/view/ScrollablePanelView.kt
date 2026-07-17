package battletech.tui.view

import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

internal class ScrollablePanelView(
    private val index: Int,
    private val title: String,
    private val content: View,
    private val scrollOffset: Int?,
    private val anchorBottom: Boolean = false,
) : View {

    var maxOffset: Int = 0
        private set

    companion object {
        private const val MAX_CONTENT_ROWS = 512
        private val GREEN_STYLE = Cell.Style(Color.GREEN)
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, title, index = index)

        if (width <= 4 || height <= 2) {
            maxOffset = 0
            return
        }

        val contentWidth = width - 4
        val scratch = ScreenBuffer(contentWidth, MAX_CONTENT_ROWS)
        content.render(scratch, 0, 0, contentWidth, MAX_CONTENT_ROWS)

        val contentHeight = measureContentHeight(scratch)
        val viewportHeight = height - 2
        maxOffset = (contentHeight - viewportHeight).coerceAtLeast(0)

        val offset = (scrollOffset ?: if (anchorBottom) maxOffset else 0).coerceIn(0, maxOffset)

        buffer.blit(scratch, 0, offset, x + 2, y + 1, contentWidth, viewportHeight)

        val thumbRange = Scrollbar.thumb(
            track = viewportHeight,
            contentHeight = contentHeight,
            viewportHeight = viewportHeight,
            offset = offset,
        )
        if (thumbRange != null) {
            for (row in thumbRange) {
                buffer.set(x + width - 1, y + 1 + row, Cell("▐", GREEN_STYLE))
            }
        }
    }

    private fun measureContentHeight(scratch: ScreenBuffer): Int {
        for (row in scratch.height - 1 downTo 0) {
            for (col in 0 until scratch.width) {
                val cell = scratch.get(col, row)
                if (cell.char != " " || cell.style.bg != Color.DEFAULT) {
                    return row + 1
                }
            }
        }
        return 0
    }
}
