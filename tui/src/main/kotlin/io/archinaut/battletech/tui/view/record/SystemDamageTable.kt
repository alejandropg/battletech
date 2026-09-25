package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tactical.unit.CombatUnit
import io.archinaut.battletech.tactical.unit.ComponentCritStatus
import io.archinaut.battletech.tactical.unit.criticalDamageStatus
import io.archinaut.battletech.tui.icon.emptyCircleIcon
import io.archinaut.battletech.tui.icon.filledCircleIcon
import io.archinaut.battletech.tui.view.MechLabels
import io.archinaut.battletech.tui.view.formatCritEffect
import io.archinaut.tenter.view.TextCursor
import io.archinaut.battletech.tui.view.PreparedTextView
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
