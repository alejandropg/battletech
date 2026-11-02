package io.archinaut.battletech.tui.view

import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.widget.RowWriter

/** One `── TITLE ─────` rule across the whole width, opening a section of a panel or card. */
internal class SectionHeading(private val title: String) : MeasurableWidget {

    override fun write(writer: RowWriter) {
        val prefix = "── $title "
        val fill = (writer.width - CellWidth.of(prefix)).coerceAtLeast(0)
        writer.writeLine(prefix + "─".repeat(fill), STYLE)
    }

    private companion object {
        private val STYLE = Style(StandardRole.INFO)
    }
}
