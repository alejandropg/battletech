package io.archinaut.battletech.tui.setup

import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.RowWriter
import io.archinaut.tenter.widget.CheckState
import io.archinaut.tenter.widget.SelectableRow
import io.archinaut.battletech.tui.icon.TUI_CHECKBOX_GLYPHS

/** Panel 2: every registered map, single-select (D6/D7-style rows, but MAP has no count column). */
internal class MapListView(
    private val maps: List<String>,
    private val selected: String?,
    private val cursorIndex: Int,
) : MeasurableWidget {

    override fun write(content: RowWriter) {
        if (maps.isEmpty()) {
            content.writeLine("No maps registered", TEXT_PRIMARY_STYLE)
            return
        }

        for ((index, name) in maps.withIndex()) {
            val isCursorHere = index == cursorIndex
            val state = if (name == selected) CheckState.CHECKED else CheckState.UNCHECKED
            content.draw(
                SelectableRow(
                    label = name,
                    checkState = state,
                    selected = isCursorHere,
                    glyphs = TUI_CHECKBOX_GLYPHS,
                ),
            )
        }
    }

    internal companion object {
        internal fun contentWidth(maps: List<String>): Int =
            4 + (maps.maxOfOrNull(CellWidth::of) ?: CellWidth.of("No maps registered"))

        private val TEXT_PRIMARY_STYLE = Style(ChromeRole.TEXT_PRIMARY)
    }
}
