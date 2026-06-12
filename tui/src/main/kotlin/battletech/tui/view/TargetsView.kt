package battletech.tui.view

import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.unit.UnitId
import battletech.tui.game.PanelId
import battletech.tui.screen.Color
import battletech.tui.screen.ContentWriter
import battletech.tui.screen.ScreenBuffer

public class TargetsView(
    private val targets: List<TargetInfo>,
    private val weaponAssignments: Map<UnitId, Set<Int>>,
    private val primaryTargetId: UnitId?,
    private val cursorTargetIndex: Int,
    private val cursorWeaponIndex: Int = 0,
) : View {

    public companion object {
        public val INDEX: Int = PanelId.TARGETS.index
        public const val TITLE: String = "TARGETS"
        private const val PADDING = 2
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "TARGETS", index = INDEX)

        val lastRow = y + height - 1
        val content = ContentWriter(buffer, x + PADDING, y + PADDING, width - (PADDING * 2))

        if (targets.isEmpty()) {
            content.writeln("No targets", Color.WHITE)
            return
        }

        for ((index, target) in targets.withIndex()) {
            if (content.cy >= lastRow) break

            val isCursorOnTarget = index == cursorTargetIndex
            val tag = when {
                primaryTargetId == null -> ""
                target.unitId == primaryTargetId -> " [P]"
                else -> " [S]"
            }
            // Target name: no arrow (arrow is on weapon line); highlight if cursor is within this target
            val nameColor = if (isCursorOnTarget) Color.BRIGHT_YELLOW else Color.WHITE
            val nameLine = "  ${target.unitName}$tag"
            content.writeln(nameLine.take(content.width), nameColor)

            val assignedToThisTarget = weaponAssignments[target.unitId] ?: emptySet()
            val assignedToOtherTargets = weaponAssignments.entries
                .filter { (k, _) -> k != target.unitId }
                .flatMap { (_, v) -> v }
                .toSet()

            for ((wi, weapon) in target.weapons.withIndex()) {
                if (content.cy >= lastRow) break

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
                val left = "$cursor $assignedElsewhereMarker ${weapon.weaponName}"
                val right = "${weapon.successChance}%"
                val padding = (content.width - left.length - right.length).coerceAtLeast(1)
                val weaponLine = "$left${" ".repeat(padding)}$right"

                val color = when {
                    isCursorHere -> Color.BRIGHT_YELLOW
                    isDisabled -> Color.GRAY
                    else -> Color.WHITE
                }
                content.writeln(weaponLine.take(content.width), color)
            }

            if (target.weapons.isNotEmpty()) {
                val availableWeapons = target.weapons.filter { it.available }
                val mods = availableWeapons.firstOrNull()?.modifiers ?: target.weapons.first().modifiers
                if (mods.isNotEmpty() && content.cy < lastRow) {
                    val modLine = "  [${mods.joinToString(", ")}]"
                    content.writeln(modLine.take(content.width), Color.DEFAULT)
                }
            }

            content.newLine() // blank line between targets
        }
    }
}
