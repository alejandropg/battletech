package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tui.screen.AppRole
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tui.game.phase.DeclaredTargetsRender
import io.archinaut.battletech.tui.game.phase.DeclaredWeaponEntry
import io.archinaut.battletech.tui.icon.targetIcon
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.widget.RowWriter

internal class DeclaredTargetsView(private val data: DeclaredTargetsRender) : MeasurableWidget {

    override fun write(content: RowWriter) {
        if (data.entries.isEmpty()) {
            content.writeLine("No declarations", TEXT_PRIMARY_STYLE)
            return
        }

        for ((index, entry) in data.entries.withIndex()) {
            val attackerColor = if (entry.isDraft) AppRole.DRAFT else playerColor(entry.ownerPlayer)
            val contentColor = if (entry.isDraft) AppRole.DRAFT else StandardRole.TEXT_PRIMARY

            content.writeLine(entry.attackerId.value, Style(attackerColor))

            for (target in entry.targets) {
                val tag = if (target.isPrimary) "[P]" else "[S]"
                val targetLine = "${targetIcon()} ${target.targetId.value} $tag"
                content.writeLine(targetLine, Style(contentColor))

                for (weapon in target.weapons) {
                    when (weapon) {
                        is DeclaredWeaponEntry.Detailed -> content.draw(
                            ValueRow(
                                "    ${weapon.weaponName}",
                                hitChanceLabel(weapon.targetDiceRoll, weapon.successChance),
                                weapon.modifiers,
                                contentColor,
                            ),
                        )
                        // Enemy attacker: name the weapon that's pointed at us (observable),
                        // but print no target number, hit chance, or modifier breakdown —
                        // that math is computed from the attacker's gunnery/heat/sensor
                        // crits. The type carries no such fields; see DeclaredWeaponEntry.
                        is DeclaredWeaponEntry.Undisclosed ->
                            content.writeLine("    ${weapon.weaponName}", Style(contentColor))
                    }
                }
            }

            // Blank line between attackers, but only if there's a next entry
            if (index < data.entries.size - 1) {
                content.newLine()
            }
        }
    }

    internal companion object {
        const val TITLE: String = "DECLARED TARGETS"

        private val TEXT_PRIMARY_STYLE = Style(StandardRole.TEXT_PRIMARY)
    }
}
