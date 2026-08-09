package battletech.tui.view

import battletech.tui.screen.Canvas
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
     */
    val PADDING: Insets = Insets(left = 1, top = 1, right = 1, bottom = 0)

    /**
     * The status bar is only [FrameLayout.STATUS_BAR_HEIGHT] = 4 rows tall: border alone
     * takes 2, and it needs 2 content rows (phase label + prompt), so it cannot afford the
     * spacer row — `top = 0`.
     */
    val STATUS_BAR_PADDING: Insets = Insets(left = 1, top = 0, right = 1, bottom = 0)

    /** Total shrink from the allotted rect to the content region: 2 / 2 / 2 / 1. */
    val CONTENT_INSET: Insets = BORDER + PADDING

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
}
