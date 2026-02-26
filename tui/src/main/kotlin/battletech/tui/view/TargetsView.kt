package battletech.tui.view

import battletech.tactical.action.UnitId
import battletech.tui.game.TargetInfo
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public class TargetsView(
    private val targets: List<TargetInfo>,
    private val weaponAssignments: Map<UnitId, Set<Int>>,
    private val primaryTargetId: UnitId?,
    private val selectedTargetIndex: Int,
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "TARGETS")

        val cx = x + 2
        var cy = y + 2

        if (targets.isEmpty()) {
            buffer.writeString(cx, cy, "No targets", Color.WHITE)
            return
        }

        for ((index, target) in targets.withIndex()) {
            if (cy >= y + height - 1) break

            val marker = if (index == selectedTargetIndex) "\u25B6 " else "  "
            val tag = when {
                primaryTargetId == null -> ""
                target.unitId == primaryTargetId -> " [P]"
                else -> " [S]"
            }
            val nameColor = if (index == selectedTargetIndex) Color.BRIGHT_YELLOW else Color.WHITE
            val line = "$marker${target.unitName}$tag"
            buffer.writeString(cx, cy, line.take(width - 4), nameColor)
            cy++

            val assigned = weaponAssignments[target.unitId] ?: emptySet()
            for ((weaponNum, weapon) in target.eligibleWeapons.withIndex()) {
                if (cy >= y + height - 1) break
                val selected = if (weapon.weaponIndex in assigned) "*" else " "
                val weaponLine = " ${weaponNum + 1}[$selected] ${weapon.weaponName} ${weapon.successChance}%"
                buffer.writeString(cx, cy, weaponLine.take(width - 4), Color.WHITE)
                cy++
            }

            // Modifiers for first weapon (representative)
            if (target.eligibleWeapons.isNotEmpty()) {
                val mods = target.eligibleWeapons.first().modifiers
                if (mods.isNotEmpty() && cy < y + height - 1) {
                    val modLine = " [${mods.joinToString(", ")}]"
                    buffer.writeString(cx, cy, modLine.take(width - 4), Color.DEFAULT)
                    cy++
                }
            }

            cy++ // blank line between targets
        }
    }
}
