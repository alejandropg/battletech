package battletech.tui.view

import battletech.tui.game.phase.DeclaredTargetsRender
import battletech.tui.game.phase.DeclaredWeaponEntry
import battletech.tui.hex.targetIcon
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.UiRole
import tenter.view.TextCursor
import tenter.widget.ValueRow
import tenter.view.View

internal class DeclaredTargetsView(private val data: DeclaredTargetsRender) : View {

    override fun render(canvas: Canvas) {
        val content = TextCursor(canvas)

        if (data.entries.isEmpty()) {
            content.writeLine("No declarations", TEXT_PRIMARY_STYLE)
            return
        }

        for ((index, entry) in data.entries.withIndex()) {
            val attackerColor = if (entry.isDraft) UiRole.DRAFT else playerColor(entry.ownerPlayer)
            val contentColor = if (entry.isDraft) UiRole.DRAFT else UiRole.TEXT_PRIMARY

            content.writeLine(entry.attackerId.value, Cell.Style(attackerColor))

            for (target in entry.targets) {
                val tag = if (target.isPrimary) "[P]" else "[S]"
                val targetLine = "${targetIcon()} ${target.targetId.value} $tag"
                content.writeLine(targetLine, Cell.Style(contentColor))

                for (weapon in target.weapons) {
                    when (weapon) {
                        is DeclaredWeaponEntry.Detailed -> ValueRow.draw(
                            content,
                            "    ${weapon.weaponName}",
                            hitChanceLabel(weapon.targetDiceRoll, weapon.successChance),
                            weapon.modifiers,
                            contentColor,
                        )
                        // Enemy attacker: name the weapon that's pointed at us (observable),
                        // but print no target number, hit chance, or modifier breakdown —
                        // that math is computed from the attacker's gunnery/heat/sensor
                        // crits. The type carries no such fields; see DeclaredWeaponEntry.
                        is DeclaredWeaponEntry.Undisclosed ->
                            content.writeLine("    ${weapon.weaponName}", Cell.Style(contentColor))
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

        private val TEXT_PRIMARY_STYLE = Cell.Style(UiRole.TEXT_PRIMARY)
    }
}
