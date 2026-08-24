package battletech.tui.view.record

import battletech.tactical.unit.ForeignUnit
import battletech.tui.view.ForeignWeaponList
import tenter.screen.Canvas
import tenter.view.Columns
import tenter.view.Stack
import tenter.view.TextCursor
import tenter.view.View

/**
 * The maximized record sheet for a unit the viewer does NOT own: 'Mech data, weapon names, and
 * the armor diagram are placed in the same grid slots as the owner sheet — a blank
 * [WARRIOR_DATA_WIDTH]-wide column stands in for the WARRIOR DATA card so the WEAPONS column
 * lines up at the same x-offset either sheet uses. No warrior data, heat, crit table, system
 * damage, or internal structure diagram is drawn because [ForeignUnit] carries none of that
 * private data. [MechDataCard] already prints [unit]'s name/id, so nothing repeats it above the
 * grid.
 */
internal class ForeignRecordSheetView(private val unit: ForeignUnit) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        val upperSections = Columns(
            listOf(
                Columns.Child(SheetLayout.MECH_DATA_WIDTH, MechDataCard(unit)),
                Columns.Child(SheetLayout.WARRIOR_DATA_WIDTH, View.None),
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

    private class ForeignWeaponInventory(private val unit: ForeignUnit) : View {
        override fun draw(canvas: Canvas) {
            val content = TextCursor(canvas)
            content.writeHeader("WEAPONS & EQUIPMENT INVENTORY")
            content.draw(ForeignWeaponList(unit))
        }
    }
}
