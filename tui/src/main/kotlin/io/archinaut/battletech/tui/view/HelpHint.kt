package io.archinaut.battletech.tui.view

import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Cell
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.text.CellWidth

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
