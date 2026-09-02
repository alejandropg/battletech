package battletech.tui.setup

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.screen.RevealRect
import tenter.view.View

/**
 * Composes the two content views used by a maximized setup panel. The result remains one content
 * stream, which lets the enclosing [tenter.panel.Panel] scroll the list, divider, and detail view
 * together and preserves selection auto-follow from the left-hand view.
 */
internal class SplitMaximizedView(
    private val leftWidth: Int,
    private val left: View,
    private val detail: View,
) : View {

    override fun draw(canvas: Canvas) {
        if (canvas.width <= 0 || canvas.height <= 0) return

        val actualLeftWidth = leftWidth.coerceAtMost(canvas.width).coerceAtLeast(1)
        val remainingWidth = canvas.width - actualLeftWidth
        val leftGutter = minOf(SIDE_GUTTER, (remainingWidth - DIVIDER_WIDTH).coerceAtLeast(0))
        val dividerWidth = if (remainingWidth > leftGutter) DIVIDER_WIDTH else 0
        val detailX = actualLeftWidth + leftGutter + dividerWidth +
            minOf(SIDE_GUTTER, (remainingWidth - leftGutter - dividerWidth).coerceAtLeast(0))
        val detailWidth = canvas.width - detailX

        val leftCanvas = Canvas.offscreen(actualLeftWidth, canvas.height)
        left.draw(leftCanvas)

        val detailCanvas = if (detailWidth > 0) Canvas.offscreen(detailWidth, canvas.height) else null
        detailCanvas?.let(detail::draw)

        val contentHeight = maxOf(leftCanvas.contentHeight(), detailCanvas?.contentHeight() ?: 0)
        canvas.blit(leftCanvas, 0, 0, 0, 0, actualLeftWidth, contentHeight)
        detailCanvas?.let { canvas.blit(it, 0, 0, detailX, 0, detailWidth, contentHeight) }

        if (dividerWidth == 1) {
            val dividerStyle = Cell.Style(ChromeRole.PANEL_BORDER)
            for (row in 0 until contentHeight) canvas.set(actualLeftWidth + leftGutter, row, Cell("│", dividerStyle))
        }

        // A selection in the left view is the usual reveal target. Keep the adapter general by
        // also translating a reveal from the detail side when there is no left-side target.
        val reveal = leftCanvas.revealRect() ?: detailCanvas?.revealRect()?.translateX(detailX)
        reveal?.let { canvas.markReveal(it.x, it.y, it.width, it.height) }
    }

    private fun RevealRect.translateX(delta: Int): RevealRect = copy(x = x + delta)

    internal companion object {
        /** Four blank columns frame each side of the divider in maximized setup views. */
        const val SIDE_GUTTER: Int = 4
        const val DIVIDER_WIDTH: Int = 1

        internal fun totalWidth(leftWidth: Int, detailWidth: Int): Int =
            leftWidth + SIDE_GUTTER + DIVIDER_WIDTH + SIDE_GUTTER + detailWidth

        /** Measures a detail view at its natural width for a fixed maximized viewport extent. */
        internal fun contentHeight(view: View, width: Int): Int {
            val canvas = Canvas.offscreen(width, MEASURE_HEIGHT)
            view.draw(canvas)
            return canvas.contentHeight()
        }

        private const val MEASURE_HEIGHT: Int = 512
    }
}
