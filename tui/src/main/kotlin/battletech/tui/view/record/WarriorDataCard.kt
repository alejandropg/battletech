package battletech.tui.view.record

import battletech.tactical.attack.consciousnessTarget
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.PILOT_DEATH_THRESHOLD
import battletech.tui.hex.emptyCircleIcon
import battletech.tui.hex.filledCircleIcon
import battletech.tui.hex.pilotDeadIcon
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
        val hits = unit.pilotHits.coerceIn(0, PILOT_DEATH_THRESHOLD)
        var col = 0
        for (i in 0 until PILOT_DEATH_THRESHOLD) {
            val filled = i < hits
            val icon = when {
                !filled -> emptyCircleIcon()
                i == PILOT_DEATH_THRESHOLD - 1 -> pilotDeadIcon()
                else -> filledCircleIcon()
            }
            val style = if (filled) SheetStyles.DANGER else SheetStyles.TEXT_PRIMARY
            content.write(col, icon, style)
            col += 2
        }
        content.newLine()

        content.writeLine("Consciousness#", SheetStyles.TEXT_MUTED)
        col = 0
        for (i in 1..PILOT_DEATH_THRESHOLD) {
            val label = consciousnessTarget(i)?.toString() ?: "Dead"
            content.write(col, label, SheetStyles.TEXT_MUTED)
            col += 3
        }
        content.newLine()
    }
}
