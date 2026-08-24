package battletech.tui.view.record

import battletech.tactical.model.MechLocation
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.CriticalSlotContent
import battletech.tactical.unit.SLOT_COUNTS
import battletech.tactical.unit.isSlotDestroyed
import battletech.tui.view.MechLabels
import tenter.screen.Canvas
import tenter.view.Columns
import tenter.view.TextCursor
import tenter.view.View

/**
 * The CRITICAL HIT TABLE card: five 30-column lanes spanning the full record sheet, with the head
 * and center torso sharing the middle lane, each leg below its adjacent torso, and system damage
 * below the left arm. [Columns] keeps each complete lane together when a narrower terminal forces
 * the table to wrap.
 */
internal class CriticalHitTable(private val unit: CombatUnit) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        content.writeHeader("CRITICAL HIT TABLE")

        val columns = Columns(
            listOf(
                criticalColumn(listOf(MechLocation.LEFT_ARM), includesSystemDamage = true),
                criticalColumn(listOf(MechLocation.LEFT_TORSO, MechLocation.LEFT_LEG)),
                criticalColumn(listOf(MechLocation.HEAD, MechLocation.CENTER_TORSO)),
                criticalColumn(listOf(MechLocation.RIGHT_TORSO, MechLocation.RIGHT_LEG)),
                criticalColumn(listOf(MechLocation.RIGHT_ARM)),
            ),
            gutter = 0,
        )
        drawBand(canvas, content, columns)
    }

    private fun criticalColumn(
        locations: List<MechLocation>,
        includesSystemDamage: Boolean = false,
    ): Columns.Child = Columns.Child(
        SheetLayout.CRIT_COLUMN_WIDTH,
        CriticalColumn(unit, locations, includesSystemDamage),
    )

    /** One complete 30-column lane, containing one or two locations and optional system damage. */
    private class CriticalColumn(
        private val unit: CombatUnit,
        private val locations: List<MechLocation>,
        private val includesSystemDamage: Boolean,
    ) : View {

        override fun draw(canvas: Canvas) {
            val content = TextCursor(canvas)
            locations.forEachIndexed { index, location ->
                if (index > 0) content.newLine()
                writeLocation(content, location)
            }

            if (includesSystemDamage) {
                repeat(SYSTEM_DAMAGE_GAP) { content.newLine() }
                SystemDamageTable(unit).draw(
                    canvas.region(
                        x = 0,
                        y = content.row,
                        width = SheetLayout.SYSTEM_DAMAGE_WIDTH,
                        height = canvas.height - content.row,
                    ),
                )
            }
        }

        private fun writeLocation(content: TextCursor, location: MechLocation) {
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
