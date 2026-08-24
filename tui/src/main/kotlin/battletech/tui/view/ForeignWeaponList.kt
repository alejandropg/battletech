package battletech.tui.view

import battletech.tactical.unit.ForeignUnit
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.TextCursor
import tenter.view.View

/**
 * One indented line per [ForeignUnit.weapons] entry, name only — no location/damage/ammo, since
 * that detail isn't public for a unit the viewer doesn't own. Shared by the NORMAL
 * [ForeignUnitPanel] and the maximized record sheet's `ForeignRecordSheetView`, which each write
 * their own header ("WEAPONS" vs. "WEAPONS & EQUIPMENT INVENTORY") before drawing this.
 */
internal class ForeignWeaponList(private val unit: ForeignUnit) : View {
    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        for (weapon in unit.weapons) content.writeLine("  ${weapon.name}", TEXT_PRIMARY_STYLE)
    }

    private companion object {
        private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    }
}
