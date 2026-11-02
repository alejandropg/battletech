package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tui.view.SectionHeading
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.unit.CombatUnit
import io.archinaut.battletech.tactical.unit.ComponentCritStatus
import io.archinaut.battletech.tactical.unit.criticalDamageStatus
import io.archinaut.battletech.tui.icon.emptyCircleIcon
import io.archinaut.battletech.tui.icon.filledCircleIcon
import io.archinaut.battletech.tui.view.MechLabels
import io.archinaut.battletech.tui.view.formatCritEffect
import io.archinaut.tenter.widget.RowWriter

/** The SYSTEM DAMAGE card: hit tracks and penalties for the 'Mech's shared components. */
internal class SystemDamageTable(private val unit: CombatUnit) : MeasurableWidget {

    override fun write(content: RowWriter) {
        content.draw(SectionHeading("SYSTEM DAMAGE"))
        val track = PipTrack(filledCircleIcon(), emptyCircleIcon(), perRow = 12)
        for (status in unit.criticalDamageStatus()) {
            writeComponentStatus(content, track, status)
        }
    }

    private fun writeComponentStatus(content: RowWriter, track: PipTrack, status: ComponentCritStatus) {
        val label = MechLabels.component(status.component).padEnd(PIP_COLUMN)
        val row = content.row
        content.write(0, label, SheetStyles.TEXT_PRIMARY)
        track.forEachPip(
            used = status.hits,
            capacity = status.capacity,
            usedStyle = SheetStyles.DANGER,
            emptyStyle = SheetStyles.TEXT_PRIMARY,
        ) { column, pipRow, glyph, style -> content.write(PIP_COLUMN + column, glyph, style, row = row + pipRow) }
        repeat(track.rows(status.capacity)) { content.newLine() }
        for (penalty in status.penalties) content.writeLine("  ${formatCritEffect(penalty)}", SheetStyles.DANGER)
    }

    private companion object {
        /** Where each component's pip track starts, past its padded label. */
        private const val PIP_COLUMN: Int = 14
    }
}
