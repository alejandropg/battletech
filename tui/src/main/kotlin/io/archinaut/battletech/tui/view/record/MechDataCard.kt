package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tactical.unit.VisibleUnit
import io.archinaut.battletech.tui.view.SpecialUnitStatusList
import io.archinaut.battletech.tui.view.UnitLabel
import io.archinaut.tenter.view.TextCursor
import io.archinaut.battletech.tui.view.PreparedTextView

/**
 * The 'MECH DATA card: identity, tonnage, movement points, and public special statuses —
 * everything the record sheet's top-left box shows that's visible for ANY unit, own or enemy.
 * [VisibleUnit]-typed rather than
 * [io.archinaut.battletech.tactical.unit.CombatUnit]-typed so the same card serves both
 * [io.archinaut.battletech.tui.view.record.MechRecordSheetView] and [ForeignRecordSheetView].
 */
internal class MechDataCard(private val unit: VisibleUnit) : PreparedTextView() {

    override fun render(content: TextCursor) {
        content.writeHeader("'MECH DATA")
        content.writeLine(UnitLabel.of(unit), SheetStyles.ACCENT)
        content.writeLine("Tonnage : ${unit.tonnage}", SheetStyles.TEXT_PRIMARY)
        content.writeLine("Walking : ${unit.walkingMP}", SheetStyles.TEXT_PRIMARY)
        content.writeLine("Running : ${unit.runningMP}", SheetStyles.TEXT_PRIMARY)
        if (unit.jumpMP > 0) content.writeLine("Jumping : ${unit.jumpMP}", SheetStyles.TEXT_PRIMARY)
        content.draw(SpecialUnitStatusList(unit))
    }
}
