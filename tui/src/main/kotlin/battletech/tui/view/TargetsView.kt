package battletech.tui.view

import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.attack.weapon.WeaponTargetInfo
import battletech.tactical.unit.UnitId
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.widget.CheckState
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.SelectableRow

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

                val color = if (isDisabled) ChromeRole.DISABLED else ChromeRole.TEXT_PRIMARY
                when (weapon) {
                    is WeaponTargetInfo.Available ->
                        SelectableRow.draw(
                            content = content,
                            label = weapon.weaponName,
                            checkState = state,
                            cursor = isCursorHere,
                            right = hitChanceLabel(weapon.toHit),
                            subLines = weapon.toHit.displayLabels(),
                            textColor = color,
                            checkboxColor = if (isDisabled) ChromeRole.DISABLED else null,
                        )
                    is WeaponTargetInfo.Unavailable ->
                        SelectableRow.draw(
                            content = content,
                            label = weapon.weaponName,
                            checkState = state,
                            cursor = isCursorHere,
                            right = "—",
                            textColor = color,
                            checkboxColor = if (isDisabled) ChromeRole.DISABLED else null,
                        )
                }
            }

            content.newLine() // blank line between targets
        }
    }

    internal companion object {
        internal const val TITLE: String = "TARGETS"

        private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    }
}
