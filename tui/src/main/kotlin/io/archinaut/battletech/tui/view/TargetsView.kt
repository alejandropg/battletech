package io.archinaut.battletech.tui.view

import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.attack.weapon.TargetInfo
import io.archinaut.battletech.tactical.attack.weapon.WeaponTargetInfo
import io.archinaut.battletech.tactical.unit.UnitId
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.widget.CheckState
import io.archinaut.tenter.widget.RowWriter
import io.archinaut.tenter.widget.SelectableRow
import io.archinaut.battletech.tui.icon.TUI_CHECKBOX_GLYPHS

internal class TargetsView(
    private val targets: List<TargetInfo>,
    private val weaponAssignments: Map<UnitId, Set<Int>>,
    private val primaryTargetId: UnitId?,
    private val cursorTargetIndex: Int,
    private val cursorWeaponIndex: Int = 0,
) : MeasurableWidget {

    override fun write(content: RowWriter) {
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
            val nameColor = if (isCursorOnTarget) StandardRole.ACCENT else StandardRole.TEXT_PRIMARY
            val nameLine = "${UnitLabel.of(target.unitId, target.unitName)}$tag"
            content.writeLine(nameLine, Style(nameColor))

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

                val color = if (isDisabled) StandardRole.DISABLED else StandardRole.TEXT_PRIMARY
                when (weapon) {
                    is WeaponTargetInfo.Available -> {
                        content.draw(
                            SelectableRow(
                                label = weapon.weaponName,
                                checkState = state,
                                selected = isCursorHere,
                                right = hitChanceLabel(weapon.toHit),
                                textColor = color,
                                checkboxColor = if (isDisabled) StandardRole.DISABLED else null,
                                glyphs = TUI_CHECKBOX_GLYPHS,
                            ),
                        )
                        content.writeSubLines(weapon.toHit.displayLabels(), if (isCursorHere) StandardRole.ACCENT else color)
                    }
                    is WeaponTargetInfo.Unavailable ->
                        content.draw(
                            SelectableRow(
                                label = weapon.weaponName,
                                checkState = state,
                                selected = isCursorHere,
                                right = "—",
                                textColor = color,
                                checkboxColor = if (isDisabled) StandardRole.DISABLED else null,
                                glyphs = TUI_CHECKBOX_GLYPHS,
                            ),
                        )
                }
            }

            content.newLine() // blank line between targets
        }
    }

    internal companion object {
        internal const val TITLE: String = "TARGETS"

        private val TEXT_PRIMARY_STYLE = Style(StandardRole.TEXT_PRIMARY)
    }
}
