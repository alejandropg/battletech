package battletech.tui.view

import battletech.tactical.unit.VisibleUnit
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.TextCursor
import tenter.view.View

/**
 * Renders the visibly-observable exceptional states of [unit] as a compact, untitled block.
 * The [VisibleUnit] seam makes the same renderer safe for owned and foreign units: private record
 * sheet details are not available to inspect here. A unit with no exceptional state uses no rows.
 */
internal class SpecialUnitStatusList(private val unit: VisibleUnit) : View {

    override fun draw(canvas: Canvas) {
        val statuses = buildList {
            if (unit.isDestroyed) add("DESTROYED")
            if (unit.isShutdown) add("SHUTDOWN")
            if (!unit.isPilotConscious) add("PILOT UNCONSCIOUS")
            if (unit.isProne) add("PRONE")
        }
        if (statuses.isEmpty()) return

        val content = TextCursor(canvas)
        content.newLine()
        for (status in statuses) {
            content.writeLine(status, STATUS_STYLE)
        }
    }

    private companion object {
        private val STATUS_STYLE = Cell.Style(ChromeRole.DANGER)
    }
}
