package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tui.view.SectionHeading
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.model.MechLocation
import io.archinaut.battletech.tactical.unit.CombatUnit
import io.archinaut.battletech.tactical.unit.CriticalSlotContent
import io.archinaut.battletech.tactical.unit.SLOT_COUNTS
import io.archinaut.battletech.tactical.unit.isSlotDestroyed
import io.archinaut.battletech.tui.view.MechLabels
import io.archinaut.tenter.screen.Constraint
import io.archinaut.tenter.screen.Padding
import io.archinaut.tenter.widget.Block
import io.archinaut.tenter.widget.Flow
import io.archinaut.tenter.widget.block
import io.archinaut.tenter.widget.RowWriter

/**
 * The CRITICAL HIT TABLE card: five 30-column lanes spanning the full record sheet, with the head
 * and center torso sharing the middle lane, each leg below its adjacent torso, and system damage
 * below the left arm. [Flow] keeps each complete lane together when a narrower terminal forces
 * the table to wrap.
 */
internal class CriticalHitTable(private val unit: CombatUnit) : MeasurableWidget {

    override fun write(content: RowWriter) {
        content.draw(SectionHeading("CRITICAL HIT TABLE"))

        val columns = Flow(
            listOf(
                criticalColumn(listOf(MechLocation.LEFT_ARM), includesSystemDamage = true),
                criticalColumn(listOf(MechLocation.LEFT_TORSO, MechLocation.LEFT_LEG)),
                criticalColumn(listOf(MechLocation.HEAD, MechLocation.CENTER_TORSO)),
                criticalColumn(listOf(MechLocation.RIGHT_TORSO, MechLocation.RIGHT_LEG)),
                criticalColumn(listOf(MechLocation.RIGHT_ARM)),
            ),
            spacing = 0,
        )
        content.draw(columns)
    }

    private fun criticalColumn(
        locations: List<MechLocation>,
        includesSystemDamage: Boolean = false,
    ): Pair<Constraint.Length, MeasurableWidget> =
        Constraint.Length(SheetLayout.CRIT_COLUMN_WIDTH) to CriticalColumn(unit, locations, includesSystemDamage)

    /** One complete 30-column lane, containing one or two locations and optional system damage. */
    private class CriticalColumn(
        private val unit: CombatUnit,
        private val locations: List<MechLocation>,
        private val includesSystemDamage: Boolean,
    ) : MeasurableWidget {

        override fun write(content: RowWriter) {
            locations.forEachIndexed { index, location ->
                if (index > 0) content.newLine()
                writeLocation(content, location)
            }

            if (includesSystemDamage) {
                repeat(SYSTEM_DAMAGE_GAP) { content.newLine() }
                val margin = SheetLayout.CRIT_COLUMN_WIDTH - SheetLayout.SYSTEM_DAMAGE_WIDTH
                content.draw(SystemDamageTable(unit).block(Block(padding = Padding(right = margin))))
            }
        }

        private fun writeLocation(content: RowWriter, location: MechLocation) {
            content.writeLine(MechLabels.location(location).uppercase(), SheetStyles.ACCENT)
            val slots = unit.criticalLayout.slotsAt(location)
            val slotCount = SLOT_COUNTS.getValue(location)
            for (index in 0 until slotCount) {
                val destroyed = unit.isSlotDestroyed(location, index)
                val label = when (val slot = slots.getOrNull(index)) {
                    null, CriticalSlotContent.Empty -> ""
                    else -> MechLabels.criticalSlotContent(slot) { unit.weapons }
                }
                val style = if (destroyed) SheetStyles.DESTROYED else SheetStyles.TEXT_PRIMARY
                content.writeLine("${(index + 1).toString().padStart(2)}. $label", style)
            }
        }

        private companion object {
            private const val SYSTEM_DAMAGE_GAP: Int = 3
        }
    }
}
