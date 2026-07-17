package battletech.tui.view

import battletech.tui.hex.checkboxIcon
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

/** Reusable single-cell NerdFont checkbox glyph. */
public object Checkbox {

    /** Default per-state color when the surrounding row does not override it. */
    public fun intrinsicColor(state: CheckState): Color = when (state) {
        CheckState.CHECKED -> Color.BRIGHT_GREEN
        else -> Color.GRAY
    }

    /** Draws the checkbox at (x, y); occupies exactly one cell. */
    public fun draw(
        buffer: ScreenBuffer,
        x: Int,
        y: Int,
        state: CheckState,
        color: Color = intrinsicColor(state),
    ) {
        buffer.writeString(x, y, checkboxIcon(state), Cell.Style(color))
    }
}
