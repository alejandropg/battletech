package tenter.widget

import tenter.screen.Cell
import tenter.screen.ColorRole
import tenter.view.TextCursor

/** Renders "<left> … <right>" then one indented line per entry in [subLines], all in [color]. */
public object ValueRow {
    public fun draw(
        content: TextCursor,
        left: String,
        right: String,
        subLines: List<String>,
        color: ColorRole,
    ) {
        content.writeRow(left, right, Cell.Style(color))
        subLines.forEach { content.writeLine("    $it", Cell.Style(color)) }
    }
}
