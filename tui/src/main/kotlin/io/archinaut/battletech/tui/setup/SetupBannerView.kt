package io.archinaut.battletech.tui.setup

import io.archinaut.tenter.screen.Line
import com.github.ajalt.mordant.rendering.TextAlign
import io.archinaut.battletech.tui.view.HelpHint
import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Constraint.Fill
import io.archinaut.tenter.screen.Constraint.Length
import io.archinaut.tenter.screen.Constraint.Min
import io.archinaut.tenter.screen.Layout
import io.archinaut.tenter.screen.Padding
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.Block
import io.archinaut.tenter.widget.Paragraph
import io.archinaut.tenter.widget.Widget

/**
 * Top chrome for the setup screen (D19): one bordered panel holding the banner (or, when the
 * terminal is too narrow for it, a plain centered title line), with a blank spacer row below the
 * top border, plus a final row carrying
 * [prompt]/flash text on the left and a right-aligned [HelpHint.LABEL] label — modeled directly on
 * `io.archinaut.battletech.tui.view.StatusBarView`. Not focusable, not in the `1`-`4` panel cycle.
 */
internal class SetupBannerView(private val prompt: String) : Widget {

    override fun render(canvas: Canvas) {
        val block = Block.bordered(padding = BANNER_PADDING)
        block.render(canvas)
        renderContent(block.inner(canvas))
    }

    private fun renderContent(canvas: Canvas) {
        val titleLines = if (canvas.width >= bannerWidth) BANNER_LINES else listOf(FALLBACK_TITLE)
        Paragraph(titleLines.map { Line(it, ACCENT_STYLE) }, align = TextAlign.CENTER)
            .render(canvas.region(Rect(0, 0, canvas.width, titleLines.size)))

        // The prompt stops short of the help hint, so an over-long one ellipsizes before it.
        val (promptArea, _, hintArea) = HINT_ROW.split(Rect(0, titleLines.size, canvas.width, 1))
        Paragraph(prompt, TEXT_PRIMARY_STYLE).render(canvas.region(promptArea))
        HelpHint.render(canvas.region(hintArea))
    }

    internal companion object {
        internal const val FALLBACK_TITLE = "BATTLETECH"
        private const val MESSAGE_HELP_GAP = 1
        // Min, so the message and the gap give way to the hint before it shrinks.
        private val HINT_ROW = Layout.horizontal(Fill(1), Length(MESSAGE_HELP_GAP), Min(HelpHint.WIDTH))

        private val ACCENT_STYLE = Style(StandardRole.ACCENT)
        private val TEXT_PRIMARY_STYLE = Style(StandardRole.TEXT_PRIMARY)
        private val BANNER_PADDING = Padding(left = 1, top = 1, right = 1)

        private val bannerWidth: Int = BANNER_LINES.maxOf { CellWidth.of(it) }

        /** The full-frame (not content-inset) width at and above which the banner itself fits. */
        private val WIDE_THRESHOLD: Int = bannerWidth + BORDER_WIDTH + BANNER_PADDING.left + BANNER_PADDING.right + BORDER_WIDTH

        /** One border cell on each side of a bordered [Block]. */
        private const val BORDER_WIDTH: Int = 1

        /** Rows this view's chrome consumes: border + spacer + banner/title + the status row. */
        internal fun reservedHeight(width: Int): Int = if (width >= WIDE_THRESHOLD) BANNER_LINES.size + 4 else 5
    }
}
