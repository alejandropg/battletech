package battletech.tui.setup

import battletech.tui.view.HelpHint
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
 * terminal is too narrow for it, a plain centered title line), with a blank spacer row below the
 * top border, plus a final row carrying
 * [prompt]/flash text on the left and a right-aligned [HelpHint.LABEL] label — modeled directly on
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

            val helpColumn = HelpHint.column(canvas)
            val messageWidth = (helpColumn - MESSAGE_HELP_GAP).coerceAtLeast(0)
            cursor.write(0, TextTruncation.ellipsize(prompt, messageWidth), TEXT_PRIMARY_STYLE)
            HelpHint.draw(canvas, cursor.row)
            cursor.newLine()
        }
    }

    internal companion object {
        internal const val FALLBACK_TITLE = "BATTLETECH"
        private const val MESSAGE_HELP_GAP = 1

        private val ACCENT_STYLE = Cell.Style(ChromeRole.ACCENT)
        private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
        private val BANNER_PADDING = Insets(left = 1, top = 1, right = 1)

        private val bannerWidth: Int = BANNER_LINES.maxOf { CellWidth.of(it) }

        /** The full-frame (not content-inset) width at and above which the banner itself fits. */
        private val WIDE_THRESHOLD: Int = bannerWidth + Bordered.BORDER.left + Bordered.BORDER.right +
            BANNER_PADDING.left + BANNER_PADDING.right

        /** Rows this view's chrome consumes: border + spacer + banner/title + the status row. */
        internal fun reservedHeight(width: Int): Int = if (width >= WIDE_THRESHOLD) BANNER_LINES.size + 4 else 5
    }
}
