package io.archinaut.battletech.tui.view

import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.unit.ForeignUnit
import io.archinaut.tenter.widget.RowWriter

internal class TargetStatusView(private val unit: ForeignUnit) : MeasurableWidget {

    override fun write(content: RowWriter) {
        ForeignUnitPanel.render(content, unit)
    }

    internal companion object {
        internal const val TITLE: String = "TARGET STATUS"
    }
}
