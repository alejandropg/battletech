package io.archinaut.battletech.tui.setup

import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Cell
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.screen.Size
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.widget.RowWriter
import io.archinaut.tenter.widget.Widget
import io.archinaut.tenter.widget.fixedSize
import io.archinaut.tenter.widget.measure

/**
 * Composition for a maximized setup panel: [left] at [leftWidth], then — when there is a [detail] —
 * a divider and the detail at whatever width it needs, so a detail wider than the panel scrolls
 * horizontally rather than wrapping below the list. When both request visibility, [left]'s
 * request wins: it holds the cursor.
 */
internal class SplitMaximizedView(
    private val leftWidth: Int,
    private val left: MeasurableWidget,
    private val detail: MeasurableWidget?,
) : MeasurableWidget {

    override fun write(writer: RowWriter) {
        val detailAvailableWidth = (writer.width - leftWidth - SIDE_GUTTER - DIVIDER_WIDTH - SIDE_GUTTER).coerceAtLeast(0)
        val detailSize = detail?.measure(detailAvailableWidth)
        val height = maxOf(left.measure(leftWidth).height, detailSize?.height ?: 0)
        writer.draw(Split(height, detailSize).fixedSize(totalWidth(leftWidth, detailSize?.width ?: 0), height))
    }

    private inner class Split(private val height: Int, private val detailSize: Size?) : Widget {
        override fun render(canvas: Canvas) {
            if (detail != null && detailSize != null) {
                val dividerX = leftWidth + SIDE_GUTTER
                for (row in 0 until height) canvas.set(dividerX, row, Cell("│", DIVIDER_STYLE))
                val detailX = dividerX + DIVIDER_WIDTH + SIDE_GUTTER
                detail.render(canvas.region(Rect(detailX, 0, detailSize.width, height)))
            }
            // Rendered last, so its reveal request — the list cursor — is the one that wins.
            left.render(canvas.region(Rect(0, 0, leftWidth, height)))
        }
    }

    internal companion object {
        const val SIDE_GUTTER: Int = 4
        const val DIVIDER_WIDTH: Int = 1

        private val DIVIDER_STYLE = Style(StandardRole.PANEL_BORDER)

        internal fun totalWidth(leftWidth: Int, detailWidth: Int): Int =
            leftWidth + SIDE_GUTTER + if (detailWidth > 0) DIVIDER_WIDTH + SIDE_GUTTER + detailWidth else 0
    }
}
