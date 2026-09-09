package battletech.tui.view

import battletech.tactical.unit.ForeignUnit
import tenter.view.TextCursor

internal class TargetStatusView(private val unit: ForeignUnit) : PreparedTextView() {

    override fun render(content: TextCursor) {
        ForeignUnitPanel.render(content, unit)
    }

    internal companion object {
        internal const val TITLE: String = "TARGET STATUS"
    }
}
