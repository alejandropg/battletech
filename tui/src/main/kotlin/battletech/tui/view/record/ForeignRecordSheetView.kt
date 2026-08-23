package battletech.tui.view.record

import battletech.tactical.unit.ForeignUnit
import battletech.tui.view.UnitLabel
import tenter.screen.Canvas
import tenter.view.Columns
import tenter.view.Stack
import tenter.view.TextCursor
import tenter.view.View

/**
 * The maximized record sheet for a unit the viewer does NOT own: 'Mech data, weapon names, and
 * the armor diagram are placed in the same grid slots as the owner sheet. No warrior data, heat,
 * crit table, system damage, or internal structure diagram is drawn because [ForeignUnit] carries
 * none of that private data.
 */
internal object ForeignRecordSheetView {

    fun render(canvas: Canvas, content: TextCursor, unit: ForeignUnit) {
        content.writeLine(UnitLabel.of(unit), SheetStyles.ACCENT)
        content.newLine()

        val upperSections = Columns(
            listOf(
                Columns.Child(SheetLayout.MECH_DATA_WIDTH, MechDataCard(unit)),
                Columns.Child(SheetLayout.WARRIOR_DATA_WIDTH, EmptyView),
                Columns.Child(SheetLayout.WEAPON_INVENTORY_WIDTH, ForeignWeaponInventory(unit)),
            ),
        )
        val diagrams = Columns(
            listOf(
                Columns.Child(SheetLayout.ARMOR_DIAGRAM_WIDTH, RecordSheetDiagrams.armor(unit)),
            ),
        )

        drawBand(
            canvas,
            content,
            Columns(
                listOf(
                    Columns.Child(
                        SheetLayout.MAIN_CONTENT_WIDTH,
                        Stack(listOf(upperSections, diagrams), gutter = 2),
                    ),
                ),
            ),
        )
    }

    private object EmptyView : View {
        override fun draw(canvas: Canvas) = Unit
    }

    private class ForeignWeaponInventory(private val unit: ForeignUnit) : View {
        override fun draw(canvas: Canvas) {
            val content = TextCursor(canvas)
            content.writeHeader("WEAPONS & EQUIPMENT INVENTORY")
            for (weapon in unit.weapons) {
                content.writeLine("  ${weapon.name}", SheetStyles.TEXT_PRIMARY)
            }
        }
    }
}
