package battletech.tui.view

import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.Insets

/**
 * The box every framed view shares. Draws the border on [Canvas.drawBox] and returns the
 * content region inside the border and its padding — the single place a panel's padding
 * is declared, so no view computes its own offset.
 */
internal object PanelChrome {
    /** One cell on each side, consumed by [Canvas.drawBox]. */
    private val BORDER: Insets = Insets.all(1)

    /**
     * Breathing room between the border and the content. `top = 1` is the blank spacer row
     * under the title, uniform with the left/right gutters; `bottom = 0` leaves content
     * flush against the bottom border.
     *
     * The horizontal gutters are frame — fixed for the panel's lifetime — but [top] is NOT:
     * for a scrolling panel it is prepended to the content stream (see [drawScrollable])
     * rather than baked into the viewport, so it is visible at rest and the content reclaims
     * its row the moment the user scrolls.
     */
    val PADDING: Insets = Insets(left = 1, top = 1, right = 1, bottom = 0)

    /**
     * The status bar is only [FrameLayout.STATUS_BAR_HEIGHT] = 4 rows tall: border alone
     * takes 2, and it needs 2 content rows (phase label + prompt), so it cannot afford the
     * spacer row — `top = 0`.
     */
    val STATUS_BAR_PADDING: Insets = Insets(left = 1, top = 0, right = 1, bottom = 0)

    /**
     * A scrolling panel's viewport: border plus the horizontal gutters, at full inner height.
     * Public because it also describes where the tactical board sits in absolute screen
     * coordinates (see `battletech.tui.game.phase.SelectingCommon`'s click-mapping) — not just
     * an implementation detail of [drawScrollable]/[drawThumbs].
     */
    val VIEWPORT_INSET: Insets = BORDER + PADDING.horizontal()

    private val GREEN_STYLE = Cell.Style(Color.GREEN)

    fun draw(
        canvas: Canvas,
        title: String = "",
        badge: String? = null,
        padding: Insets = PADDING,
        borderColor: Color = Color.GREEN,
        titleColor: Color = Color.BRIGHT_YELLOW,
    ): Canvas {
        canvas.drawBox(title, badge, borderColor, titleColor)
        return canvas.inset(BORDER + padding)
    }

    /**
     * Chrome for a scrolling panel: the [ScrollChrome.viewport] to blit content into, at full
     * inner height, and the [ScrollChrome.streamPadding] the caller must inset its content
     * stream with before scrolling it through that viewport — [PADDING]'s vertical component
     * belongs to the content stream, not the viewport itself.
     */
    fun drawScrollable(canvas: Canvas, title: String, badge: String?): ScrollChrome {
        canvas.drawBox(title, badge)
        return ScrollChrome(canvas.inset(VIEWPORT_INSET), PADDING.vertical())
    }

    /**
     * Draws a scrollbar thumb on [canvas]'s right border ([vertical]) and/or bottom border
     * ([horizontal]), at the ranges [Scrollbar.thumb] computed — either may be `null` when that
     * axis doesn't overflow. [canvas] is the same bordered canvas [drawScrollable] drew into,
     * not its returned viewport.
     */
    fun drawThumbs(canvas: Canvas, vertical: IntRange?, horizontal: IntRange?) {
        vertical?.forEach { i -> canvas.set(canvas.width - 1, VIEWPORT_INSET.top + i, Cell("▐", GREEN_STYLE)) }
        horizontal?.forEach { i -> canvas.set(VIEWPORT_INSET.left + i, canvas.height - 1, Cell("▬", GREEN_STYLE)) }
    }
}

/** What a scrolling panel needs from its chrome: the [viewport] to blit into and the padding its content stream must carry. */
internal data class ScrollChrome(val viewport: Canvas, val streamPadding: Insets)
