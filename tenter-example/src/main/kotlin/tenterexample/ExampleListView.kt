package tenterexample

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.TextCursor
import tenter.view.View

internal class ExampleListView(
    private val rowCount: Int,
) : View {
    override fun draw(canvas: Canvas) {
        val cursor = TextCursor(canvas)
        repeat(rowCount) { index ->
            val label = when (index % 3) {
                0 -> "row $index — ordinary text"
                1 -> "row $index — 中 rendered as a wide glyph"
                else -> "row $index — decomposed e\u0301 accent"
            }
            cursor.writeLine(label, if (index == 0) Cell.Style(fg = ChromeRole.ACCENT) else Cell.Style.DEFAULT)
        }
    }
}
