package io.archinaut.battletech.tui.view

import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.widget.Block
import io.archinaut.tenter.widget.Borders
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.widget.RowWriter
import io.archinaut.tenter.widget.Title

/** One `─── TITLE ─────` rule across the whole width, opening a section of a panel or card. */
internal class SectionHeading(title: String) : MeasurableWidget {

    private val rule = Block(
        title = Title(title),
        borders = Borders.TOP,
        borderColor = StandardRole.INFO,
        titleColor = StandardRole.INFO,
    )

    override fun write(writer: RowWriter) {
        writer.draw(rule, 1)
    }
}
