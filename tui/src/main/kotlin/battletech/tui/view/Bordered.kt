package battletech.tui.view

import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.Insets

/**
 * Decorates [content] with a box border, an optional title/badge in the top border, and — when
 * [thumbsFrom] is given — scrollbar thumbs synchronized to that [Scrolled]'s settled position.
 * [gutters] is extra inset consumed between the border and [content], beyond the 1-cell border
 * itself (e.g. the horizontal breathing room a scrolling panel's viewport wants); pass
 * [Insets.NONE] for a border with nothing but the border between it and content.
 *
 * The single place every framed view gets its box from — no view draws its own border.
 */
internal class Bordered(
    private val content: View,
    private val title: String = "",
    private val badge: String? = null,
    private val gutters: Insets = Insets.NONE,
    private val borderColor: Color = Color.GREEN,
    private val titleColor: Color = Color.BRIGHT_YELLOW,
    private val thumbsFrom: Scrolled? = null,
) : View {

    /** What [thumbsFrom] settled on this render, or [ScrollState.NONE] if this box doesn't scroll. */
    val scroll: ScrollState get() = thumbsFrom?.scroll ?: ScrollState.NONE

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

        Scrollbar.thumb(
            track = viewportHeight,
            contentHeight = scroll.maxOffset.y + viewportHeight,
            viewportHeight = viewportHeight,
            offset = scroll.offset.y,
        )?.forEach { i -> canvas.set(canvas.width - 1, inset.top + i, Cell("▐", GREEN_STYLE)) }

        Scrollbar.thumb(
            track = viewportWidth,
            contentHeight = scroll.maxOffset.x + viewportWidth,
            viewportHeight = viewportWidth,
            offset = scroll.offset.x,
        )?.forEach { i -> canvas.set(inset.left + i, canvas.height - 1, Cell("▬", GREEN_STYLE)) }
    }

    internal companion object {
        private val GREEN_STYLE = Cell.Style(Color.GREEN)

        /** One cell on each side, consumed by every [Bordered] box. */
        val BORDER: Insets = Insets.all(1)

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
        val PADDING: Insets = Insets(left = 1, top = 1, right = 1, bottom = 0)

        /**
         * The status bar is only [Workspace.STATUS_BAR_HEIGHT] = 4 rows tall: border alone
         * takes 2, and it needs 2 content rows (phase label + prompt), so it cannot afford the
         * spacer row — `top = 0`.
         */
        val STATUS_BAR_PADDING: Insets = Insets(left = 1, top = 0, right = 1, bottom = 0)

        /**
         * A scrolling panel's viewport: border plus the horizontal gutters, at full inner height.
         * Public because it also describes where the tactical board sits in absolute screen
         * coordinates (see `battletech.tui.game.phase.SelectingCommon`'s click-mapping) — not
         * just an implementation detail of [scrollingPanel].
         */
        val VIEWPORT_INSET: Insets = BORDER + PADDING.horizontal()
    }
}
