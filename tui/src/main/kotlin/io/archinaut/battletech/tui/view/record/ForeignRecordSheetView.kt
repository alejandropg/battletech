package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tui.view.SectionHeading
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.unit.ForeignUnit
import io.archinaut.battletech.tui.view.ForeignWeaponList
import io.archinaut.tenter.screen.Constraint
import io.archinaut.tenter.widget.Flow
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
        val upperSections = Flow(
            listOf(
                Constraint.Length(SheetLayout.MECH_DATA_WIDTH) to MechDataCard(unit),
                Constraint.Length(SheetLayout.WARRIOR_DATA_WIDTH) to MeasurableWidget { },
                Constraint.Length(SheetLayout.WEAPON_INVENTORY_WIDTH) to ForeignWeaponInventory(unit),
            ),
        )
        val diagrams = Flow(
            listOf(
                Constraint.Length(SheetLayout.ARMOR_DIAGRAM_WIDTH) to LocationDiagram.armor(unit) { false },
            ),
        )
        content.draw(Stack(listOf(upperSections, diagrams), spacing = 2))
    }

    private class ForeignWeaponInventory(private val unit: ForeignUnit) : MeasurableWidget {
        override fun write(content: RowWriter) {
            content.draw(SectionHeading("WEAPONS & EQUIPMENT INVENTORY"))
            content.draw(ForeignWeaponList(unit))
        }
    }
}
