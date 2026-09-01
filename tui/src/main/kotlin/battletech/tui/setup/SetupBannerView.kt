package battletech.tui.setup

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.screen.Insets
import tenter.text.CellWidth
import tenter.text.TextTruncation
import tenter.view.Bordered
import tenter.view.TextCursor
import tenter.view.View

/**
 * Top chrome for the setup screen (D19): one bordered panel holding the banner (or, when the
 * terminal is too narrow for it, a plain centered title line) plus a final row carrying
 * [prompt]/flash text on the left and a right-aligned `? help` label — modeled directly on
 * `battletech.tui.view.StatusBarView`. Not focusable, not in the `1`-`4` panel cycle.
 */
internal class SetupBannerView(private val prompt: String) : View {

    override fun draw(canvas: Canvas) {
        Bordered(gutters = BANNER_PADDING, content = Content()).draw(canvas)
    }

    private inner class Content : View {
        override fun draw(canvas: Canvas) {
            val cursor = TextCursor(canvas)

            if (canvas.width >= bannerWidth) {
                for (line in BANNER_LINES) {
                    val left = ((canvas.width - CellWidth.of(line)) / 2).coerceAtLeast(0)
                    cursor.write(left, line, ACCENT_STYLE)
                    cursor.newLine()
                }
            } else {
                val left = ((canvas.width - CellWidth.of(FALLBACK_TITLE)) / 2).coerceAtLeast(0)
                cursor.write(left, FALLBACK_TITLE, ACCENT_STYLE)
                cursor.newLine()
            }

            val helpColumn = canvas.width - HELP_WIDTH
            val messageWidth = (helpColumn - MESSAGE_HELP_GAP).coerceAtLeast(0)
            cursor.write(0, TextTruncation.ellipsize(prompt, messageWidth), TEXT_PRIMARY_STYLE)
            if (helpColumn >= 0) canvas.writeString(helpColumn, cursor.row, HELP_LABEL, TEXT_PRIMARY_STYLE)
            cursor.newLine()
        }
    }

    internal companion object {
        internal const val FALLBACK_TITLE = "BATTLETECH"
        private const val HELP_LABEL = "? help"
        private const val MESSAGE_HELP_GAP = 1

        private val ACCENT_STYLE = Cell.Style(ChromeRole.ACCENT)
        private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
        private val HELP_WIDTH = CellWidth.of(HELP_LABEL)
        private val BANNER_PADDING = Insets(left = 1, right = 1)

        private val bannerWidth: Int = BANNER_LINES.maxOf { CellWidth.of(it) }

        /** The full-frame (not content-inset) width at and above which the banner itself fits. */
        private val WIDE_THRESHOLD: Int = bannerWidth + Bordered.BORDER.left + Bordered.BORDER.right +
            BANNER_PADDING.left + BANNER_PADDING.right

        /** Rows this view's chrome consumes: border + banner (or one title row) + the status row. */
        internal fun reservedHeight(width: Int): Int = if (width >= WIDE_THRESHOLD) BANNER_LINES.size + 3 else 4
    }
}
