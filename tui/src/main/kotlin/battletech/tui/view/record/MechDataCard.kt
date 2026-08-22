package battletech.tui.view.record

import battletech.tactical.unit.VisibleUnit
import battletech.tui.view.UnitLabel
import tenter.screen.Canvas
import tenter.view.TextCursor
import tenter.view.View

/**
 * The 'MECH DATA card: identity, tonnage, and movement points — everything the record sheet's
 * top-left box shows that's visible for ANY unit, own or enemy. [VisibleUnit]-typed rather than
 * [battletech.tactical.unit.CombatUnit]-typed so the same card serves both
 * [battletech.tui.view.record.MechRecordSheetView] and [ForeignRecordSheetView].
 */
internal class MechDataCard(private val unit: VisibleUnit) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        content.writeHeader("'MECH DATA")
        content.writeLine(UnitLabel.of(unit), SheetStyles.ACCENT)
        content.writeLine("Tonnage : ${unit.tonnage}", SheetStyles.TEXT_PRIMARY)
        content.writeLine("Walking : ${unit.walkingMP}", SheetStyles.TEXT_PRIMARY)
        content.writeLine("Running : ${unit.runningMP}", SheetStyles.TEXT_PRIMARY)
        if (unit.jumpMP > 0) content.writeLine("Jumping : ${unit.jumpMP}", SheetStyles.TEXT_PRIMARY)

        val flags = buildList {
            if (unit.isDestroyed) add("DESTROYED")
            if (unit.isShutdown) add("SHUTDOWN")
            if (!unit.isPilotConscious) add("PILOT UNCONSCIOUS")
            if (unit.isProne) add("PRONE")
        }
        if (flags.isNotEmpty()) {
            content.newLine()
            content.writeLine(flags.joinToString("  "), SheetStyles.DANGER)
        }
    }
}
