package io.archinaut.battletech.tui.view.record

import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.model.GameMap
import io.archinaut.battletech.tactical.unit.CombatUnit
import io.archinaut.battletech.tactical.unit.HeatSource
import io.archinaut.tenter.screen.Constraint
import io.archinaut.tenter.widget.Flow
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
        val upperSections = Flow(
            listOf(
                Constraint.Length(SheetLayout.MECH_DATA_WIDTH) to MechDataCard(unit),
                Constraint.Length(SheetLayout.WARRIOR_DATA_WIDTH) to WarriorDataCard(unit),
                Constraint.Length(SheetLayout.WEAPON_INVENTORY_WIDTH) to WeaponInventoryTable(unit),
            ),
        )
        val diagrams = Flow(
            listOf(
                Constraint.Length(SheetLayout.ARMOR_DIAGRAM_WIDTH) to LocationDiagram.armor(unit) { !unit.internalStructure.isIntact(it) },
                Constraint.Length(SheetLayout.INTERNAL_STRUCTURE_DIAGRAM_WIDTH) to LocationDiagram.internalStructure(unit),
            ),
        )
        val mainContent = Stack(
            listOf(
                upperSections,
                diagrams,
            ),
            spacing = 2,
        )
        val upperBand = Flow(
            listOf(
                Constraint.Length(SheetLayout.MAIN_CONTENT_WIDTH) to mainContent,
                Constraint.Length(SheetLayout.HEAT_WIDTH) to HeatLadder(unit, map, pendingHeat),
            ),
        )

        content.draw(Stack(listOf(upperBand, CriticalHitTable(unit)), spacing = 2))
    }
}
