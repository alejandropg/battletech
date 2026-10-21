package io.archinaut.battletech.tui.view.record

import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.model.GameMap
import io.archinaut.battletech.tactical.unit.CombatUnit
import io.archinaut.battletech.tactical.unit.HeatSource
import io.archinaut.tenter.widget.Column
import io.archinaut.tenter.widget.Columns
import io.archinaut.tenter.widget.Stack
import io.archinaut.tenter.widget.RowWriter

/**
 * The maximized record sheet for a unit the viewer owns: every private card — warrior data, the
 * heat ladder, the internal structure diagram, the full critical hit table — alongside the cards
 * a foreign unit also shows. [MechDataCard] already prints [unit]'s name/id, so nothing repeats
 * it above the grid.
 */
internal class OwnRecordSheetView(
    private val unit: CombatUnit,
    private val map: GameMap,
    private val pendingHeat: List<HeatSource>,
) : MeasurableWidget {

    override fun write(content: RowWriter) {
        val upperSections = Columns(
            listOf(
                Column(SheetLayout.MECH_DATA_WIDTH, MechDataCard(unit)),
                Column(SheetLayout.WARRIOR_DATA_WIDTH, WarriorDataCard(unit)),
                Column(SheetLayout.WEAPON_INVENTORY_WIDTH, WeaponInventoryTable(unit)),
            ),
        )
        val diagrams = Columns(
            listOf(
                Column(
                    SheetLayout.ARMOR_DIAGRAM_WIDTH,
                    LocationDiagram.armor(unit) { !unit.internalStructure.isIntact(it) },
                ),
                Column(
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
                Column(SheetLayout.MAIN_CONTENT_WIDTH, mainContent),
                Column(SheetLayout.HEAT_WIDTH, HeatLadder(unit, map, pendingHeat)),
            ),
        )

        content.draw(Stack(listOf(upperBand, CriticalHitTable(unit)), gutter = 2))
    }
}
