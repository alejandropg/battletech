package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tactical.unit.ForeignUnit
import io.archinaut.battletech.tui.view.ForeignWeaponList
import io.archinaut.tenter.view.Columns
import io.archinaut.tenter.view.Stack
import io.archinaut.tenter.view.TextCursor
import io.archinaut.tenter.view.ContentView
import io.archinaut.tenter.view.contentView
import io.archinaut.battletech.tui.view.PreparedTextView

/**
 * The maximized record sheet for a unit the viewer does NOT own: 'Mech data, weapon names, and
 * the armor diagram are placed in the same grid slots as the owner sheet — a blank
 * [WARRIOR_DATA_WIDTH]-wide column stands in for the WARRIOR DATA card so the WEAPONS column
 * lines up at the same x-offset either sheet uses. No warrior data, heat, crit table, system
 * damage, or internal structure diagram is drawn because [ForeignUnit] carries none of that
 * private data. [MechDataCard] already prints [unit]'s name/id, so nothing repeats it above the
 * grid.
 */
internal class ForeignRecordSheetView(private val unit: ForeignUnit) : PreparedTextView() {

    override fun render(content: TextCursor) {
        val upperSections = Columns(
            listOf(
                Columns.Child(SheetLayout.MECH_DATA_WIDTH, MechDataCard(unit)),
                Columns.Child(SheetLayout.WARRIOR_DATA_WIDTH, contentView { }),
                Columns.Child(SheetLayout.WEAPON_INVENTORY_WIDTH, ForeignWeaponInventory(unit)),
            ),
        )
        val diagrams = Columns(
            listOf(
                Columns.Child(SheetLayout.ARMOR_DIAGRAM_WIDTH, LocationDiagram.armor(unit) { false }),
            ),
        )
        content.draw(Stack(listOf(upperSections, diagrams), gutter = 2))
    }

    private class ForeignWeaponInventory(private val unit: ForeignUnit) : PreparedTextView() {
        override fun render(content: TextCursor) {
            content.writeHeader("WEAPONS & EQUIPMENT INVENTORY")
            content.draw(ForeignWeaponList(unit))
        }
    }
}
