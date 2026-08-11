package battletech.tui.view

import battletech.tactical.unit.ForeignUnit
import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color

internal class TargetStatusView(private val unit: ForeignUnit?) : View {

    override fun render(canvas: Canvas) {
        val content = ContentWriter(canvas)

        if (unit == null) {
            content.writeln("No target selected", TEXT_PRIMARY_STYLE)
            return
        }

        ForeignUnitPanel.render(content, unit)
    }

    internal companion object {
        internal const val TITLE: String = "TARGET STATUS"

        private val TEXT_PRIMARY_STYLE = Cell.Style(Color.TEXT_PRIMARY)
    }
}
