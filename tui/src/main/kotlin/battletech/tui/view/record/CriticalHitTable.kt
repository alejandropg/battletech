package battletech.tui.view.record

import battletech.tactical.model.MechLocation
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.ComponentCritStatus
import battletech.tactical.unit.CriticalComponent
import battletech.tactical.unit.SLOT_COUNTS
import battletech.tactical.unit.criticalDamageStatus
import battletech.tactical.unit.isSlotDestroyed
import battletech.tui.hex.emptyCircleIcon
import battletech.tui.hex.filledCircleIcon
import battletech.tui.view.MechLabels
import tenter.screen.Canvas
import tenter.view.Columns
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.PipTrack

/**
 * The CRITICAL HIT TABLE card: every location's numbered slot list, destroyed slots struck
 * through, followed by the Engine/Gyro/Sensor/Life Support hit-dot rows
 * [battletech.tactical.unit.criticalDamageStatus] already reports for
 * [battletech.tui.view.UnitStatusView]'s "Critical hit points" section. The 8 location columns
 * are handed to [Columns] as one list — its own wrap-to-a-new-band logic is what turns them into
 * "4 columns × 2 rows" at full sheet width, and fewer per row on a narrower terminal, with no
 * manual banding here.
 */
internal class CriticalHitTable(private val unit: CombatUnit) : View {

    private val locationOrder = listOf(
        MechLocation.HEAD, MechLocation.LEFT_ARM, MechLocation.LEFT_TORSO, MechLocation.CENTER_TORSO,
        MechLocation.RIGHT_TORSO, MechLocation.RIGHT_ARM, MechLocation.LEFT_LEG, MechLocation.RIGHT_LEG,
    )

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)

        val columns = Columns(locationOrder.map { location -> Columns.Child(SheetLayout.CRIT_COLUMN_WIDTH, LocationColumn(unit, location)) })
        drawBand(canvas, content, columns)
        content.newLine()

        content.writeHeader("SYSTEM DAMAGE")
        val track = PipTrack(filledCircleIcon(), emptyCircleIcon(), perRow = 12)
        for (status in unit.criticalDamageStatus()) {
            writeComponentStatus(content, track, status)
        }
    }

    private fun writeComponentStatus(content: TextCursor, track: PipTrack, status: ComponentCritStatus) {
        val label = componentLabel(status.component).padEnd(14)
        content.write(0, label, SheetStyles.TEXT_PRIMARY)
        content.writePips(track, column = 14, used = status.hits, capacity = status.capacity, usedStyle = SheetStyles.DANGER, emptyStyle = SheetStyles.TEXT_PRIMARY)
        for (penalty in status.penalties) content.writeLine("  $penalty", SheetStyles.DANGER)
    }

    private fun componentLabel(component: CriticalComponent): String = when (component) {
        CriticalComponent.ENGINE -> "Engine"
        CriticalComponent.GYRO -> "Gyro"
        CriticalComponent.SENSOR -> "Sensor"
        CriticalComponent.LIFE_SUPPORT -> "Life Support"
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
