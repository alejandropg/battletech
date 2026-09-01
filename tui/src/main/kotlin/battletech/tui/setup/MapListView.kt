package battletech.tui.setup

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.CheckState
import tenter.widget.Checkbox

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
            val cursorGlyph = if (isCursorHere) "▶" else " "
            val color = if (isCursorHere) ChromeRole.ACCENT else ChromeRole.TEXT_PRIMARY
            val checkboxColor = if (isCursorHere) ChromeRole.ACCENT else Checkbox.intrinsicColor(state)

            val row = content.row
            if (isCursorHere) content.markReveal()
            // One space placeholder at column 2 is where the checkbox glyph is overlaid below.
            content.writeLine("$cursorGlyph   $name", Cell.Style(color))
            Checkbox.draw(content, 2, row, state, checkboxColor)
        }
    }

    private companion object {
        val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    }
}
