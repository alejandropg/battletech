package battletech.tui.view

import battletech.tactical.unit.ForeignUnit
import tenter.screen.Canvas
import tenter.view.TextCursor
import tenter.view.View

internal class TargetStatusView(private val unit: ForeignUnit) : View {

    override fun draw(canvas: Canvas) {
        ForeignUnitPanel.render(TextCursor(canvas), unit)
    }

    internal companion object {
        internal const val TITLE: String = "TARGET STATUS"
    }
}
