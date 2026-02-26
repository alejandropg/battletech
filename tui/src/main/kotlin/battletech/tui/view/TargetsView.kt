package battletech.tui.view

import battletech.tactical.action.UnitId
import battletech.tui.game.TargetInfo
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public class TargetsView(
    private val targets: List<TargetInfo>,
    private val weaponAssignments: Map<UnitId, Set<Int>>,
    private val primaryTargetId: UnitId?,
    /** Cursor position. -1 = no cursor. targets.size = "No Attack" entry selected. */
    private val selectedTargetIndex: Int,
    /** Show weapon details (eligible weapons with [*] marks). */
    private val showWeapons: Boolean = true,
    /** Show "No Attack" entry at the bottom of the list. */
    private val showNoAttack: Boolean = false,
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "TARGETS")

        val cx = x + 2
        var cy = y + 2

        if (targets.isEmpty() && !showNoAttack) {
            buffer.writeString(cx, cy, "No targets", Color.WHITE)
            return
        }

        for ((index, target) in targets.withIndex()) {
            if (cy >= y + height - 1) break

            val isSelected = index == selectedTargetIndex
            val marker = if (isSelected) "\u25B6 " else "  "
            val tag = when {
                primaryTargetId == null -> ""
                target.unitId == primaryTargetId -> " [P]"
                else -> " [S]"
            }
            val nameColor = if (isSelected) Color.BRIGHT_YELLOW else Color.WHITE
            val line = "$marker${target.unitName}$tag"
            buffer.writeString(cx, cy, line.take(width - 4), nameColor)
            cy++

            if (showWeapons) {
                val assigned = weaponAssignments[target.unitId] ?: emptySet()
                for ((_, weapon) in target.eligibleWeapons.withIndex()) {
                    if (cy >= y + height - 1) break
                    val selected = if (weapon.weaponIndex in assigned) "*" else " "
                    val weaponLine = "  [$selected] ${weapon.weaponName} ${weapon.successChance}%"
                    buffer.writeString(cx, cy, weaponLine.take(width - 4), Color.WHITE)
                    cy++
                }

                if (target.eligibleWeapons.isNotEmpty()) {
                    val mods = target.eligibleWeapons.first().modifiers
                    if (mods.isNotEmpty() && cy < y + height - 1) {
                        val modLine = "  [${mods.joinToString(", ")}]"
                        buffer.writeString(cx, cy, modLine.take(width - 4), Color.DEFAULT)
                        cy++
                    }
                }

                cy++ // blank line between targets
            }
        }

        if (showNoAttack && cy < y + height - 1) {
            val isSelected = selectedTargetIndex >= targets.size
            val marker = if (isSelected) "\u25B6 " else "  "
            val color = if (isSelected) Color.BRIGHT_YELLOW else Color.DEFAULT
            buffer.writeString(cx, cy, "${marker}No Attack", color)
        }
    }
}
