package battletech.tui.view.record

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.ComponentCritStatus
import battletech.tactical.unit.CriticalComponent
import battletech.tactical.unit.criticalDamageStatus
import battletech.tui.hex.emptyCircleIcon
import battletech.tui.hex.filledCircleIcon
import tenter.screen.Canvas
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.PipTrack

/** The SYSTEM DAMAGE card: hit tracks and penalties for the 'Mech's shared components. */
internal class SystemDamageTable(private val unit: CombatUnit) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        content.writeHeader("SYSTEM DAMAGE")
        val track = PipTrack(filledCircleIcon(), emptyCircleIcon(), perRow = 12)
        for (status in unit.criticalDamageStatus()) {
            writeComponentStatus(content, track, status)
        }
    }

    private fun writeComponentStatus(content: TextCursor, track: PipTrack, status: ComponentCritStatus) {
        val label = componentLabel(status.component).padEnd(14)
        content.write(0, label, SheetStyles.TEXT_PRIMARY)
        content.writePips(
            track,
            column = 14,
            used = status.hits,
            capacity = status.capacity,
            usedStyle = SheetStyles.DANGER,
            emptyStyle = SheetStyles.TEXT_PRIMARY,
        )
        for (penalty in status.penalties) content.writeLine("  $penalty", SheetStyles.DANGER)
    }

    private fun componentLabel(component: CriticalComponent): String = when (component) {
        CriticalComponent.ENGINE -> "Engine"
        CriticalComponent.GYRO -> "Gyro"
        CriticalComponent.SENSOR -> "Sensor"
        CriticalComponent.LIFE_SUPPORT -> "Life Support"
    }
}
