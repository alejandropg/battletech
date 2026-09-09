package tenterexample

import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.ContentLayout
import tenter.view.ContentView
import tenter.view.contentView

internal class ExampleListView(
    private val rowCount: Int,
) : ContentView {
    override fun layout(availableWidth: Int): ContentLayout = contentView { cursor ->
        repeat(rowCount) { index ->
            val label = when (index % 3) {
                0 -> "row $index — ordinary text"
                1 -> "row $index — 中 rendered as a wide glyph"
                else -> "row $index — decomposed e\u0301 accent"
            }
            cursor.writeLine(label, if (index == 0) Cell.Style(fg = ChromeRole.ACCENT) else Cell.Style.DEFAULT)
        }
    }.layout(availableWidth)
}
