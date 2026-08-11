package battletech.tui.view

import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.unit.UnitId
import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color

internal class TargetsView(
    private val targets: List<TargetInfo>,
    private val weaponAssignments: Map<UnitId, Set<Int>>,
    private val primaryTargetId: UnitId?,
    private val cursorTargetIndex: Int,
    private val cursorWeaponIndex: Int = 0,
) : View {

    override fun render(canvas: Canvas) {
        val content = ContentWriter(canvas)

        if (targets.isEmpty()) {
            content.writeln("No targets", TEXT_PRIMARY_STYLE)
            return
        }

        for ((index, target) in targets.withIndex()) {
            val isCursorOnTarget = index == cursorTargetIndex
            val tag = when {
                primaryTargetId == null -> ""
                target.unitId == primaryTargetId -> " [P]"
                else -> " [S]"
            }
            val nameColor = if (isCursorOnTarget) Color.ACCENT else Color.TEXT_PRIMARY
            val nameLine = "${UnitLabel.of(target.unitId, target.unitName)}$tag"
            content.writeln(nameLine, Cell.Style(nameColor))

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

                val color = when {
                    isCursorHere -> Color.ACCENT
                    isDisabled -> Color.DISABLED
                    else -> Color.TEXT_PRIMARY
                }
                val checkboxColor = when {
                    isCursorHere -> Color.ACCENT
                    isDisabled -> Color.DISABLED
                    else -> Checkbox.intrinsicColor(state)
                }
                val row = content.row
                if (isCursorHere) content.markFocus()
                WeaponHitWidget.draw(content, left, weapon.targetDiceRoll, weapon.successChance, weapon.modifiers, color)
                Checkbox.draw(content, 2, row, state, checkboxColor)
            }

            content.newLine() // blank line between targets
        }
    }

    internal companion object {
        internal const val TITLE: String = "TARGETS"

        private val TEXT_PRIMARY_STYLE = Cell.Style(Color.TEXT_PRIMARY)
    }
}
