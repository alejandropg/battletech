package battletech.tui.view

import battletech.tactical.action.UnitId
import battletech.tui.game.TargetInfo
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public class TargetsView(
    private val targets: List<TargetInfo>,
    private val weaponAssignments: Map<UnitId, Set<Int>>,
    private val primaryTargetId: UnitId?,
    private val cursorTargetIndex: Int,
    private val cursorWeaponIndex: Int = 0,
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

            val isCursorOnTarget = index == cursorTargetIndex
            val tag = when {
                primaryTargetId == null -> ""
                target.unitId == primaryTargetId -> " [P]"
                else -> " [S]"
            }
            // Target name: no arrow (arrow is on weapon line); highlight if cursor is within this target
            val nameColor = if (isCursorOnTarget) Color.BRIGHT_YELLOW else Color.WHITE
            val nameLine = "  ${target.unitName}$tag"
            buffer.writeString(cx, cy, nameLine.take(width - 4), nameColor)
            cy++

            val assignedToThisTarget = weaponAssignments[target.unitId] ?: emptySet()
            val assignedToOtherTargets = weaponAssignments.entries
                .filter { (k, _) -> k != target.unitId }
                .flatMap { (_, v) -> v }
                .toSet()

            for ((wi, weapon) in target.weapons.withIndex()) {
                if (cy >= y + height - 1) break

                val isCursorHere = isCursorOnTarget && wi == cursorWeaponIndex
                val isAssignedElsewhere = weapon.weaponIndex in assignedToOtherTargets
                val isAssignedHere = weapon.weaponIndex in assignedToThisTarget
                val isDisabled = !weapon.available || isAssignedElsewhere

                val mark = when {
                    isAssignedHere -> "*"
                    else -> " "
                }
                val assignedElsewhereMarker = if (isAssignedElsewhere) "[-]" else "[$mark]"

                val cursor = if (isCursorHere) "\u25B6" else " "
                val availableWidth = width - 4
                val left = "$cursor $assignedElsewhereMarker ${weapon.weaponName}"
                val right = "${weapon.successChance}%"
                val padding = (availableWidth - left.length - right.length).coerceAtLeast(1)
                val weaponLine = "$left${" ".repeat(padding)}$right"

                val color = when {
                    isCursorHere -> Color.BRIGHT_YELLOW
                    isDisabled -> Color.GRAY
                    else -> Color.WHITE
                }
                buffer.writeString(cx, cy, weaponLine.take(availableWidth), color)
                cy++
            }

            if (target.weapons.isNotEmpty()) {
                val availableWeapons = target.weapons.filter { it.available }
                val mods = availableWeapons.firstOrNull()?.modifiers ?: target.weapons.first().modifiers
                if (mods.isNotEmpty() && cy < y + height - 1) {
                    val modLine = "  [${mods.joinToString(", ")}]"
                    buffer.writeString(cx, cy, modLine.take(width - 4), Color.DEFAULT)
                    cy++
                }
            }

            cy++ // blank line between targets
        }
    }
}
