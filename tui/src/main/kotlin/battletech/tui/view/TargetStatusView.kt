package battletech.tui.view

import battletech.tactical.unit.ForeignUnit
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.TextCursor
import tenter.view.View

internal class TargetStatusView(private val unit: ForeignUnit?) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)

        if (unit == null) {
            content.writeLine("No target selected", TEXT_PRIMARY_STYLE)
            return
        }

        ForeignUnitPanel.render(content, unit)
    }

    internal companion object {
        internal const val TITLE: String = "TARGET STATUS"

        private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    }
}
