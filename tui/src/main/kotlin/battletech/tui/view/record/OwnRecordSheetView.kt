package battletech.tui.view.record

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.HeatSource
import tenter.screen.Canvas
import tenter.view.Columns
import tenter.view.Stack
import tenter.view.TextCursor
import tenter.view.View

/**
 * The maximized record sheet for a unit the viewer owns: every private card — warrior data, the
 * heat ladder, the internal structure diagram, the full critical hit table — alongside the cards
 * a foreign unit also shows. [MechDataCard] already prints [unit]'s name/id, so nothing repeats
 * it above the grid.
 */
internal class OwnRecordSheetView(
    private val unit: CombatUnit,
    private val pendingHeat: List<HeatSource>,
) : View {

    override fun draw(canvas: Canvas) {
        val upperSections = Columns(
            listOf(
                Columns.Child(SheetLayout.MECH_DATA_WIDTH, MechDataCard(unit)),
                Columns.Child(SheetLayout.WARRIOR_DATA_WIDTH, WarriorDataCard(unit)),
                Columns.Child(SheetLayout.WEAPON_INVENTORY_WIDTH, WeaponInventoryTable(unit)),
            ),
        )
        val diagrams = Columns(
            listOf(
                Columns.Child(
                    SheetLayout.ARMOR_DIAGRAM_WIDTH,
                    LocationDiagram.armor(unit) { !unit.internalStructure.isIntact(it) },
                ),
                Columns.Child(
                    SheetLayout.INTERNAL_STRUCTURE_DIAGRAM_WIDTH,
                    LocationDiagram.internalStructure(unit),
                ),
            ),
        )
        val mainContent = Stack(
            listOf(
                upperSections,
                diagrams,
            ),
            gutter = 2,
        )
        val upperBand = Columns(
            listOf(
                Columns.Child(SheetLayout.MAIN_CONTENT_WIDTH, mainContent),
                Columns.Child(SheetLayout.HEAT_WIDTH, HeatLadder(unit, pendingHeat)),
            ),
        )

        TextCursor(canvas).draw(Stack(listOf(upperBand, CriticalHitTable(unit)), gutter = 2))
    }
}
