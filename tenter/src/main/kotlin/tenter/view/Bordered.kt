package tenter.view

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ColorRole
import tenter.screen.Insets
import tenter.screen.UiRole

/**
 * Decorates [content] with a box border, an optional title/badge in the top border, and — when
 * [thumbsFrom] is given — scrollbar thumbs synchronized to that [Scrolled]'s settled position.
 * [gutters] is extra inset consumed between the border and [content], beyond the 1-cell border
 * itself (e.g. the horizontal breathing room a scrolling panel's viewport wants); pass
 * [Insets.NONE] for a border with nothing but the border between it and content.
 *
 * The single place every framed view gets its box from — no view should draw its own border.
 */
public class Bordered(
    private val content: View,
    private val title: String = "",
    private val badge: String? = null,
    private val gutters: Insets = Insets.NONE,
    private val borderColor: ColorRole = UiRole.PANEL_BORDER,
    private val titleColor: ColorRole = UiRole.ACCENT,
    private val thumbsFrom: Scrolled? = null,
) : View {

    override fun render(canvas: Canvas) {
        drawBorder(canvas)
        content.render(canvas.inset(BORDER + gutters))
        thumbsFrom?.let { drawThumbs(canvas, it) }
    }

    /** No-op below 2x2. */
    private fun drawBorder(canvas: Canvas) {
        val width = canvas.width
        val height = canvas.height
        if (width < 2 || height < 2) return

        canvas.set(0, 0, Cell("╭", Cell.Style(borderColor)))
        canvas.set(width - 1, 0, Cell("╮", Cell.Style(borderColor)))
        canvas.set(0, height - 1, Cell("╰", Cell.Style(borderColor)))
        canvas.set(width - 1, height - 1, Cell("╯", Cell.Style(borderColor)))

        for (i in 1 until width - 1) {
            canvas.set(i, 0, Cell("─", Cell.Style(borderColor)))
            canvas.set(i, height - 1, Cell("─", Cell.Style(borderColor)))
        }

        for (i in 1 until height - 1) {
            canvas.set(0, i, Cell("│", Cell.Style(borderColor)))
            canvas.set(width - 1, i, Cell("│", Cell.Style(borderColor)))
        }

        if (title.isNotEmpty()) {
            if (badge != null && width > title.length + badge.length + 7) {
                canvas.writeString(2, 0, "[$badge] $title", Cell.Style(titleColor))
                canvas.set(5 + badge.length + title.length, 0, Cell(" ", Cell.Style(borderColor)))
            } else if (badge == null && width > title.length + 6) {
                canvas.set(3, 0, Cell(" ", Cell.Style(borderColor)))
                canvas.writeString(4, 0, title, Cell.Style(titleColor))
                canvas.set(4 + title.length, 0, Cell(" ", Cell.Style(borderColor)))
            }
        } else if (badge != null && width >= 6) {
            // No title to anchor a "[badge] title" run to — e.g. a collapsed stub too narrow for
            // its full title. The badge alone is still worth showing.
            canvas.writeString(2, 0, "[$badge]", Cell.Style(titleColor))
        }
    }

    /**
     * Draws a scrollbar thumb on the right border ([ScrollState.maxOffset]`.y > 0`) and/or bottom
     * border (`.x > 0`), at the ranges [Scrollbar.thumb] computes from [scrolled]'s settled state
     * and this box's own viewport size — the same region [content] was just rendered into.
     */
    private fun drawThumbs(canvas: Canvas, scrolled: Scrolled) {
        val inset = BORDER + gutters
        val viewportWidth = canvas.width - inset.left - inset.right
        val viewportHeight = canvas.height - inset.top - inset.bottom
        val scroll = scrolled.scroll

        ScrollGeometry.thumb(
            track = viewportHeight,
            contentLength = scroll.maxOffset.y + viewportHeight,
            viewportLength = viewportHeight,
            offset = scroll.offset.y,
        )?.forEach { i -> canvas.set(canvas.width - 1, inset.top + i, Cell("▐", PANEL_BORDER_STYLE)) }

        ScrollGeometry.thumb(
            track = viewportWidth,
            contentLength = scroll.maxOffset.x + viewportWidth,
            viewportLength = viewportWidth,
            offset = scroll.offset.x,
        )?.forEach { i -> canvas.set(inset.left + i, canvas.height - 1, Cell("▬", PANEL_BORDER_STYLE)) }
    }

    public companion object {
        private val PANEL_BORDER_STYLE = Cell.Style(UiRole.PANEL_BORDER)

        /** One cell on each side, consumed by every [Bordered] box. */
        public val BORDER: Insets = Insets.all(1)

        /**
         * Breathing room between the border and the content. `top = 1` is the blank spacer row
         * under the title, uniform with the left/right gutters; `bottom = 0` leaves content
         * flush against the bottom border.
         *
         * The horizontal gutters are frame — fixed for a scrolling panel's lifetime — but [top]
         * is NOT: it is prepended to the content stream (see [scrollingPanel]) rather than baked
         * into the viewport, so it is visible at rest and the content reclaims its row the moment
         * the user scrolls.
         */
        public val PADDING: Insets = Insets(left = 1, top = 1, right = 1, bottom = 0)

        /**
         * A scrolling panel's viewport: border plus the horizontal gutters, at full inner height.
         * Public because callers that need to translate a click into content-stream coordinates
         * (e.g. mapping a screen click onto a scrolled board) need to know this inset too — not
         * just an implementation detail of [scrollingPanel].
         */
        public val VIEWPORT_INSET: Insets = BORDER + PADDING.horizontal()
    }
}
