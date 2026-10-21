package io.archinaut.battletech.tui.view

import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.unit.VisibleUnit
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.widget.RowWriter

/**
 * Renders the visibly-observable exceptional states of [unit] as a compact, untitled block.
 * The [VisibleUnit] seam makes the same renderer safe for owned and foreign units: private record
 * sheet details are not available to inspect here. A unit with no exceptional state uses no rows.
 */
internal class SpecialUnitStatusList(private val unit: VisibleUnit) : MeasurableWidget {

    override fun write(content: RowWriter) {
        val statuses = buildList {
            if (unit.isDestroyed) add("DESTROYED")
            if (unit.isShutdown) add("SHUTDOWN")
            if (!unit.isPilotConscious) add("PILOT UNCONSCIOUS")
            if (unit.isProne) add("PRONE")
        }
        if (statuses.isEmpty()) return

        content.newLine()
        for (status in statuses) {
            content.writeLine(status, STATUS_STYLE)
        }
    }

    private companion object {
        private val STATUS_STYLE = Style(ChromeRole.DANGER)
    }
}
