package tenterexample

import tenter.view.ContentLayout
import tenter.view.ContentView
import tenter.view.contentView
import tenter.widget.CheckState
import tenter.widget.CheckboxGlyphs
import tenter.widget.SelectableRow

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
            SelectableRow.draw(
                content = cursor,
                label = label,
                checkState = CheckState.UNCHECKED,
                cursor = index == 0,
                glyphs = CheckboxGlyphs.ASCII,
            )
        }
    }.layout(availableWidth)
}
