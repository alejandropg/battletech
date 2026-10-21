package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tui.view.SectionHeading
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.unit.ForeignUnit
import io.archinaut.battletech.tui.view.ForeignWeaponList
import io.archinaut.tenter.widget.Column
import io.archinaut.tenter.widget.Columns
import io.archinaut.tenter.widget.Stack
import io.archinaut.tenter.widget.RowWriter

/**
 * The maximized record sheet for a unit the viewer does NOT own: 'Mech data, weapon names, and
 * the armor diagram are placed in the same grid slots as the owner sheet — a blank
 * [WARRIOR_DATA_WIDTH]-wide column stands in for the WARRIOR DATA card so the WEAPONS column
 * lines up at the same x-offset either sheet uses. No warrior data, heat, crit table, system
 * damage, or internal structure diagram is drawn because [ForeignUnit] carries none of that
 * private data. [MechDataCard] already prints [unit]'s name/id, so nothing repeats it above the
 * grid.
 */
internal class ForeignRecordSheetView(private val unit: ForeignUnit) : MeasurableWidget {

    override fun write(content: RowWriter) {
        val upperSections = Columns(
            listOf(
                Column(SheetLayout.MECH_DATA_WIDTH, MechDataCard(unit)),
                Column(SheetLayout.WARRIOR_DATA_WIDTH, MeasurableWidget { }),
                Column(SheetLayout.WEAPON_INVENTORY_WIDTH, ForeignWeaponInventory(unit)),
            ),
        )
        val diagrams = Columns(
            listOf(
                Column(SheetLayout.ARMOR_DIAGRAM_WIDTH, LocationDiagram.armor(unit) { false }),
            ),
        )
        content.draw(Stack(listOf(upperSections, diagrams), gutter = 2))
    }

    private class ForeignWeaponInventory(private val unit: ForeignUnit) : MeasurableWidget {
        override fun write(content: RowWriter) {
            content.draw(SectionHeading("WEAPONS & EQUIPMENT INVENTORY"))
            content.draw(ForeignWeaponList(unit))
        }
    }
}
