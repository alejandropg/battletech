package io.archinaut.battletech.tui.view

import io.archinaut.tenter.palette.ColorRole
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.widget.RowWriter

/** `<left> … <right>` on one row, then one indented line per entry in [subLines], all in [color]. */
internal class ValueRow(
    private val left: String,
    private val right: String,
    private val subLines: List<String>,
    private val color: ColorRole,
) : MeasurableWidget {

    override fun write(writer: RowWriter) {
        writer.writeRow(left, right, Style(color))
        writer.writeSubLines(subLines, color)
    }
}

/** One line per entry in [subLines], indented under the row above it, in [color]. */
internal fun RowWriter.writeSubLines(subLines: List<String>, color: ColorRole) {
    subLines.forEach { writeLine("    $it", Style(color)) }
}
