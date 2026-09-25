package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tactical.unit.ForeignUnit
import io.archinaut.tenter.view.TextCursor

internal class TargetStatusView(private val unit: ForeignUnit) : PreparedTextView() {

    override fun render(content: TextCursor) {
        ForeignUnitPanel.render(content, unit)
    }

    internal companion object {
        internal const val TITLE: String = "TARGET STATUS"
    }
}
