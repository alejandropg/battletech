package battletech.tui.view

import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.attack.weapon.WeaponTargetInfo
import battletech.tactical.unit.UnitId
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.widget.CheckState
import tenter.widget.Checkbox
import tenter.view.TextCursor
import tenter.widget.ValueRow
import tenter.view.View

internal class TargetsView(
    private val targets: List<TargetInfo>,
    private val weaponAssignments: Map<UnitId, Set<Int>>,
    private val primaryTargetId: UnitId?,
    private val cursorTargetIndex: Int,
    private val cursorWeaponIndex: Int = 0,
) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)

        if (targets.isEmpty()) {
            content.writeLine("No targets", TEXT_PRIMARY_STYLE)
            return
        }

        for ((index, target) in targets.withIndex()) {
            val isCursorOnTarget = index == cursorTargetIndex
            val tag = when {
                primaryTargetId == null -> ""
                target.unitId == primaryTargetId -> " [P]"
                else -> " [S]"
            }
            val nameColor = if (isCursorOnTarget) ChromeRole.ACCENT else ChromeRole.TEXT_PRIMARY
            val nameLine = "${UnitLabel.of(target.unitId, target.unitName)}$tag"
            content.writeLine(nameLine, Cell.Style(nameColor))

            val assignedToThisTarget = weaponAssignments[target.unitId] ?: emptySet()
            val assignedToOtherTargets = weaponAssignments.entries
                .filter { (k, _) -> k != target.unitId }
                .flatMap { (_, v) -> v }
                .toSet()

            for ((wi, weapon) in target.weapons.withIndex()) {
                val isCursorHere = isCursorOnTarget && wi == cursorWeaponIndex
                val isAssignedElsewhere = weapon.weaponIndex in assignedToOtherTargets
                val isAssignedHere = weapon.weaponIndex in assignedToThisTarget
                val isDisabled = weapon !is WeaponTargetInfo.Available || isAssignedElsewhere

                val state = when {
                    isAssignedElsewhere -> CheckState.INDETERMINATE
                    isAssignedHere -> CheckState.CHECKED
                    else -> CheckState.UNCHECKED
                }

                val cursor = if (isCursorHere) "▶" else " "
                // One space placeholder at column 2 is where the checkbox glyph is overlaid below.
                val left = "$cursor   ${weapon.weaponName}"

                val color = when {
                    isCursorHere -> ChromeRole.ACCENT
                    isDisabled -> ChromeRole.DISABLED
                    else -> ChromeRole.TEXT_PRIMARY
                }
                val checkboxColor = when {
                    isCursorHere -> ChromeRole.ACCENT
                    isDisabled -> ChromeRole.DISABLED
                    else -> Checkbox.intrinsicColor(state)
                }
                val row = content.row
                if (isCursorHere) content.markReveal()
                when (weapon) {
                    is WeaponTargetInfo.Available ->
                        ValueRow.draw(content, left, hitChanceLabel(weapon.toHit), weapon.toHit.displayLabels(), color)
                    is WeaponTargetInfo.Unavailable ->
                        ValueRow.draw(content, left, "—", emptyList(), color)
                }
                Checkbox.draw(content, 2, row, state, checkboxColor)
            }

            content.newLine() // blank line between targets
        }
    }

    internal companion object {
        internal const val TITLE: String = "TARGETS"

        private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    }
}
