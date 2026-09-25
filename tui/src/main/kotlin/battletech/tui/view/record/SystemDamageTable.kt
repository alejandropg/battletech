package battletech.tui.view.record

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.ComponentCritStatus
import battletech.tactical.unit.criticalDamageStatus
import battletech.tui.icon.emptyCircleIcon
import battletech.tui.icon.filledCircleIcon
import battletech.tui.view.MechLabels
import battletech.tui.view.formatCritEffect
import io.archinaut.tenter.view.TextCursor
import battletech.tui.view.PreparedTextView
import io.archinaut.tenter.widget.PipTrack

/** The SYSTEM DAMAGE card: hit tracks and penalties for the 'Mech's shared components. */
internal class SystemDamageTable(private val unit: CombatUnit) : PreparedTextView() {

    override fun render(content: TextCursor) {
        content.writeHeader("SYSTEM DAMAGE")
        val track = PipTrack(filledCircleIcon(), emptyCircleIcon(), perRow = 12)
        for (status in unit.criticalDamageStatus()) {
            writeComponentStatus(content, track, status)
        }
    }

    private fun writeComponentStatus(content: TextCursor, track: PipTrack, status: ComponentCritStatus) {
        val label = MechLabels.component(status.component).padEnd(14)
        content.write(0, label, SheetStyles.TEXT_PRIMARY)
        track.drawAdvancing(
            content,
            column = 14,
            used = status.hits,
            capacity = status.capacity,
            usedStyle = SheetStyles.DANGER,
            emptyStyle = SheetStyles.TEXT_PRIMARY,
        )
        for (penalty in status.penalties) content.writeLine("  ${formatCritEffect(penalty)}", SheetStyles.DANGER)
    }
}
