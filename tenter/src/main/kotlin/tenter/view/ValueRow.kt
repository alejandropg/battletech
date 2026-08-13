package tenter.view

import tenter.screen.Cell
import tenter.screen.CellWidth
import tenter.screen.ColorRole

/** Renders "<left> … <right>" then one indented line per entry in [subLines], all in [color]. */
public object ValueRow {
    public fun draw(
        content: ContentWriter,
        left: String,
        right: String,
        subLines: List<String>,
        color: ColorRole,
    ) {
        val fill = (content.width - left.length - CellWidth.of(right)).coerceAtLeast(1)
        content.writeln("$left${" ".repeat(fill)}$right", Cell.Style(color))
        subLines.forEach { content.writeln("    $it", Cell.Style(color)) }
    }
}
