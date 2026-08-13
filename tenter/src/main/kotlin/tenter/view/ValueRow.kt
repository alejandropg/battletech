package tenter.view

import tenter.screen.Cell
import tenter.screen.CellWidth
import tenter.screen.ColorRole

/** Renders "<left> … <right>" then one indented line per entry in [subLines], all in [color]. */
public object ValueRow {
    public fun draw(
        content: TextCursor,
        left: String,
        right: String,
        subLines: List<String>,
        color: ColorRole,
    ) {
        val fill = (content.width - left.length - CellWidth.of(right)).coerceAtLeast(1)
        content.writeLine("$left${" ".repeat(fill)}$right", Cell.Style(color))
        subLines.forEach { content.writeLine("    $it", Cell.Style(color)) }
    }
}
