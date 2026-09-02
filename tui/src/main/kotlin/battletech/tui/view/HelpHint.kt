package battletech.tui.view

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.text.CellWidth

/** Canonical right-aligned help hint shared by the TUI's chrome views. */
internal object HelpHint {
    internal const val LABEL: String = "? : help"
    internal val WIDTH: Int = CellWidth.of(LABEL)

    internal fun column(canvas: Canvas): Int = canvas.width - WIDTH

    internal fun draw(canvas: Canvas, row: Int) {
        val column = column(canvas)
        if (column >= 0) canvas.writeString(column, row, LABEL, STYLE)
    }

    private val STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
}
