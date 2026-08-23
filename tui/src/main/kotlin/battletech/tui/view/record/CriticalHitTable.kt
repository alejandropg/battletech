package battletech.tui.view.record

import battletech.tactical.model.MechLocation
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.SLOT_COUNTS
import battletech.tactical.unit.isSlotDestroyed
import battletech.tui.view.MechLabels
import tenter.screen.Canvas
import tenter.view.Columns
import tenter.view.TextCursor
import tenter.view.View

/**
 * The CRITICAL HIT TABLE card: every location's numbered slot list, with destroyed slots struck
 * through. The 8 location columns are handed to [Columns] as one list — its own wrap-to-a-new-band
 * logic turns them into "4 columns × 2 rows" at full sheet width and fewer per row on a narrower
 * terminal.
 */
internal class CriticalHitTable(private val unit: CombatUnit) : View {

    private val locationOrder = listOf(
        MechLocation.HEAD, MechLocation.LEFT_ARM, MechLocation.LEFT_TORSO, MechLocation.CENTER_TORSO,
        MechLocation.RIGHT_TORSO, MechLocation.RIGHT_ARM, MechLocation.LEFT_LEG, MechLocation.RIGHT_LEG,
    )

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        content.writeHeader("CRITICAL HIT TABLE")

        val columns = Columns(locationOrder.map { location -> Columns.Child(SheetLayout.CRIT_COLUMN_WIDTH, LocationColumn(unit, location)) })
        drawBand(canvas, content, columns)
    }

    /** One location's numbered slot list — the [Columns] child for [location]. */
    private class LocationColumn(private val unit: CombatUnit, private val location: MechLocation) : View {
        override fun draw(canvas: Canvas) {
            val content = TextCursor(canvas)
            content.writeLine(MechLabels.location(location).uppercase(), SheetStyles.ACCENT)
            val slots = unit.criticalLayout.slotsAt(location)
            val slotCount = SLOT_COUNTS.getValue(location)
            for (index in 0 until slotCount) {
                val destroyed = unit.isSlotDestroyed(location, index)
                val label = slots.getOrNull(index)?.let { MechLabels.criticalSlotContent(it) { unit.weapons } } ?: "—"
                val style = if (destroyed) SheetStyles.DESTROYED else SheetStyles.TEXT_PRIMARY
                content.writeLine("${(index + 1).toString().padStart(2)}. $label", style)
            }
        }
    }
}
