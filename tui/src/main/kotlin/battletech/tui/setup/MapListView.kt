package battletech.tui.setup

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.text.CellWidth
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.CheckState
import tenter.widget.SelectableRow

/** Panel 2: every registered map, single-select (D6/D7-style rows, but MAP has no count column). */
internal class MapListView(
    private val maps: List<String>,
    private val selected: String?,
    private val cursorIndex: Int,
) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        if (maps.isEmpty()) {
            content.writeLine("No maps registered", TEXT_PRIMARY_STYLE)
            return
        }

        for ((index, name) in maps.withIndex()) {
            val isCursorHere = index == cursorIndex
            val state = if (name == selected) CheckState.CHECKED else CheckState.UNCHECKED
            SelectableRow.draw(
                content = content,
                label = name,
                checkState = state,
                cursor = isCursorHere,
            )
        }
    }

    internal companion object {
        internal fun contentWidth(maps: List<String>): Int =
            4 + (maps.maxOfOrNull(CellWidth::of) ?: CellWidth.of("No maps registered"))

        private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    }
}
