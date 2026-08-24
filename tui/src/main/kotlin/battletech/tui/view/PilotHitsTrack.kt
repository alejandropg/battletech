package battletech.tui.view

import battletech.tactical.unit.PILOT_DEATH_THRESHOLD
import battletech.tui.icon.emptyCircleIcon
import battletech.tui.icon.filledCircleIcon
import battletech.tui.icon.pilotDeadIcon
import tenter.screen.Cell
import tenter.view.TextCursor

/**
 * The canonical 6-box "Hits" track (record sheet Pilot Data): [hits] (coerced to
 * [PILOT_DEATH_THRESHOLD]) pips drawn with [filledStyle] — the final box using [pilotDeadIcon]
 * instead of a plain filled dot — followed by the remaining empty boxes in [emptyStyle]. Draws
 * at [column] on [content]'s current row, [stride] cells apart, without writing a label or
 * advancing the cursor: the NORMAL [UnitStatusView] and the maximized record sheet's
 * [battletech.tui.view.record.WarriorDataCard] each place their own label and spacing around it.
 */
internal object PilotHitsTrack {
    fun draw(
        content: TextCursor,
        column: Int,
        stride: Int,
        hits: Int,
        filledStyle: Cell.Style,
        emptyStyle: Cell.Style,
    ) {
        val filledCount = hits.coerceIn(0, PILOT_DEATH_THRESHOLD)
        var col = column
        for (i in 0 until PILOT_DEATH_THRESHOLD) {
            val filled = i < filledCount
            val icon = when {
                !filled -> emptyCircleIcon()
                i == PILOT_DEATH_THRESHOLD - 1 -> pilotDeadIcon()
                else -> filledCircleIcon()
            }
            content.write(col, icon, if (filled) filledStyle else emptyStyle)
            col += stride
        }
    }
}
