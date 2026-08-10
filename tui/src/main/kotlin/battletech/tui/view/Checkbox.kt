package battletech.tui.view

import battletech.tui.hex.checkboxIcon
import battletech.tui.screen.Cell
import battletech.tui.screen.Color

/** Reusable single-cell NerdFont checkbox glyph. */
internal object Checkbox {

    /** Default per-state color when the surrounding row does not override it. */
    fun intrinsicColor(state: CheckState): Color = when (state) {
        CheckState.CHECKED -> Color.BRIGHT_GREEN
        else -> Color.GRAY
    }

    /** Overlays the checkbox onto [row] at [column]; occupies exactly one cell. */
    fun draw(
        content: ContentWriter,
        column: Int,
        row: Int,
        state: CheckState,
        color: Color = intrinsicColor(state),
    ) {
        content.writeAt(column, row, checkboxIcon(state), Cell.Style(color))
    }
}
