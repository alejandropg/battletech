package io.archinaut.battletech.tui.view

import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.unit.ForeignUnit
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.widget.RowWriter

/**
 * One indented line per [ForeignUnit.weapons] entry, name only — no location/damage/ammo, since
 * that detail isn't public for a unit the viewer doesn't own. Shared by the NORMAL
 * [ForeignUnitPanel] and the maximized record sheet's `ForeignRecordSheetView`, which each write
 * their own header ("WEAPONS" vs. "WEAPONS & EQUIPMENT INVENTORY") before drawing this.
 */
internal class ForeignWeaponList(private val unit: ForeignUnit) : MeasurableWidget {
    override fun write(content: RowWriter) {
        for (weapon in unit.weapons) content.writeLine("  ${weapon.name}", TEXT_PRIMARY_STYLE)
    }

    private companion object {
        private val TEXT_PRIMARY_STYLE = Style(ChromeRole.TEXT_PRIMARY)
    }
}
