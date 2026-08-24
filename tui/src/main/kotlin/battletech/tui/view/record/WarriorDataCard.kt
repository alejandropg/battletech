package battletech.tui.view.record

import battletech.tactical.attack.consciousnessTarget
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.PILOT_DEATH_THRESHOLD
import battletech.tui.view.PilotHitsTrack
import tenter.screen.Canvas
import tenter.view.TextCursor
import tenter.view.View

/**
 * The WARRIOR DATA card: gunnery/piloting skill, the 6-box Hits Taken track, and the
 * Consciousness# row printed beneath it on the paper sheet — the 2d6 target for each hit count,
 * read from [consciousnessTarget] rather than restated here.
 */
internal class WarriorDataCard(private val unit: CombatUnit) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        content.writeHeader("WARRIOR DATA")
        content.writeLine("Gunnery  : ${unit.gunnerySkill}", SheetStyles.TEXT_PRIMARY)
        content.writeLine("Piloting : ${unit.pilotingSkill}", SheetStyles.TEXT_PRIMARY)
        content.newLine()

        content.writeLine("Hits Taken", SheetStyles.TEXT_PRIMARY)
        PilotHitsTrack.draw(
            content,
            column = 0,
            stride = 2,
            hits = unit.pilotHits,
            filledStyle = SheetStyles.DANGER,
            emptyStyle = SheetStyles.TEXT_PRIMARY,
        )
        content.newLine()

        content.writeLine("Consciousness#", SheetStyles.TEXT_MUTED)
        var col = 0
        for (i in 1..PILOT_DEATH_THRESHOLD) {
            val label = consciousnessTarget(i)?.toString() ?: "Dead"
            content.write(col, label, SheetStyles.TEXT_MUTED)
            col += 3
        }
        content.newLine()
    }
}
