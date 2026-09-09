package tenterexample

import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.Columns
import tenter.view.ContentLayout
import tenter.view.ContentView
import tenter.view.Stack
import tenter.view.contentView
import tenter.widget.CheckState
import tenter.widget.CheckboxGlyphs
import tenter.widget.SelectableRow

/** The generated catalog content, deliberately composed from prepared stack and column views. */
internal class ExampleListView(
    private val rowCount: Int,
    private val state: ExampleState,
) : ContentView {
    override fun layout(availableWidth: Int): ContentLayout {
        val markerColumn = contentView { cursor ->
            repeat(rowCount) { index ->
                cursor.writeLine(if (index == state.selectedRow) "▶" else " ", Cell.Style(ChromeRole.ACCENT))
            }
        }
        val rowColumnWidth = (availableWidth - COLUMN_GUTTER - MARKER_WIDTH).coerceAtLeast(0)
        val rowColumn = contentView { cursor ->
            repeat(rowCount) { index ->
                val label = when (index % 3) {
                    0 -> "row $index — ordinary text"
                    1 -> "row $index — 中 rendered as a wide glyph"
                    else -> "row $index — decomposed e\u0301 accent"
                }
                SelectableRow.draw(
                    content = cursor,
                    label = label,
                    checkState = if (index in state.checkedRows) CheckState.CHECKED else CheckState.UNCHECKED,
                    cursor = index == state.selectedRow,
                    glyphs = CheckboxGlyphs.ASCII,
                )
            }
        }
        val catalog = Columns(
            children = listOf(
                Columns.Child(MARKER_WIDTH, markerColumn),
                Columns.Child(rowColumnWidth, rowColumn),
            ),
            gutter = COLUMN_GUTTER,
        )
        val footer = contentView { cursor ->
            cursor.newLine()
            cursor.writeLine("600 rows • CJK 中 • decomposed e\u0301 • click or use the keyboard")
        }
        return Stack(listOf(catalog, footer)).layout(availableWidth)
    }

    private companion object {
        private const val MARKER_WIDTH: Int = 2
        private const val COLUMN_GUTTER: Int = 1
    }
}
