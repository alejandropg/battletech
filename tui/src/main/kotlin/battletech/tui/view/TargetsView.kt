package battletech.tui.view

import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.unit.UnitId
import battletech.tui.game.PanelId
import battletech.tui.hex.diceRoll
import battletech.tui.screen.CellWidth
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
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        // One blank row for pixel parity with the decorator's y+1 inner-content start
        val content = ContentWriter(buffer, x, y + 1, width)

        if (targets.isEmpty()) {
            content.writeln("No targets", Color.WHITE)
            return
        }

        for ((index, target) in targets.withIndex()) {
            val isCursorOnTarget = index == cursorTargetIndex
            val tag = when {
                primaryTargetId == null -> ""
                target.unitId == primaryTargetId -> " [P]"
                else -> " [S]"
            }
            val nameColor = if (isCursorOnTarget) Color.BRIGHT_YELLOW else Color.WHITE
            val nameLine = "${target.unitName}$tag"
            content.writeln(nameLine, nameColor)

            val assignedToThisTarget = weaponAssignments[target.unitId] ?: emptySet()
            val assignedToOtherTargets = weaponAssignments.entries
                .filter { (k, _) -> k != target.unitId }
                .flatMap { (_, v) -> v }
                .toSet()

            for ((wi, weapon) in target.weapons.withIndex()) {
                val isCursorHere = isCursorOnTarget && wi == cursorWeaponIndex
                val isAssignedElsewhere = weapon.weaponIndex in assignedToOtherTargets
                val isAssignedHere = weapon.weaponIndex in assignedToThisTarget
                val isDisabled = !weapon.available || isAssignedElsewhere

                val state = when {
                    isAssignedElsewhere -> CheckState.INDETERMINATE
                    isAssignedHere -> CheckState.CHECKED
                    else -> CheckState.UNCHECKED
                }

                val cursor = if (isCursorHere) "▶" else " "
                // One space placeholder at column 2 is where the checkbox glyph is overlaid below.
                val left = "$cursor   ${weapon.weaponName}"
                val right = "${diceRoll()}${weapon.targetDiceRoll} ${weapon.successChance}%"
                val padding = (content.width - left.length - CellWidth.of(right)).coerceAtLeast(1)
                val weaponLine = "$left${" ".repeat(padding)}$right"

                val color = when {
                    isCursorHere -> Color.BRIGHT_YELLOW
                    isDisabled -> Color.DISABLED
                    else -> Color.WHITE
                }
                val checkboxColor = when {
                    isCursorHere -> Color.BRIGHT_YELLOW
                    isDisabled -> Color.DISABLED
                    else -> Checkbox.intrinsicColor(state)
                }
                val row = content.cy
                content.writeln(weaponLine, color)
                Checkbox.draw(content.buffer, content.x + 2, row, state, checkboxColor)

                if (weapon.modifiers.isNotEmpty()) {
                    val modLine = "    [${weapon.modifiers.joinToString(", ")}]"
                    content.writeln(modLine, Color.DEFAULT)
                }
            }

            content.newLine() // blank line between targets
        }
    }
}
