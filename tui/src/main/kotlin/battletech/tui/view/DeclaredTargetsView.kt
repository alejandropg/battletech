package battletech.tui.view

import battletech.tui.game.PanelId
import battletech.tui.game.phase.DeclaredTargetsRender
import battletech.tui.game.phase.DeclaredWeaponEntry
import battletech.tui.hex.targetIcon
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ContentWriter
import battletech.tui.screen.ScreenBuffer

internal class DeclaredTargetsView(private val data: DeclaredTargetsRender) : View {

    companion object {
        val INDEX: Int = PanelId.DECLARED_TARGETS.index
        const val TITLE: String = "DECLARED TARGETS"
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        // One blank row for pixel parity with the decorator's y+1 inner-content start
        val content = ContentWriter(buffer, x, y + 1, width)

        if (data.entries.isEmpty()) {
            content.writeln("No declarations", Cell.Style(Color.WHITE))
            return
        }

        for ((index, entry) in data.entries.withIndex()) {
            val attackerColor = if (entry.isDraft) Color.DRAFT else playerColor(entry.ownerPlayer)
            val contentColor = if (entry.isDraft) Color.DRAFT else Color.WHITE

            content.writeln(entry.attackerId.value, Cell.Style(attackerColor))

            for (target in entry.targets) {
                val tag = if (target.isPrimary) "[P]" else "[S]"
                val targetLine = "${targetIcon()} ${target.targetId.value} $tag"
                content.writeln(targetLine, Cell.Style(contentColor))

                for (weapon in target.weapons) {
                    when (weapon) {
                        is DeclaredWeaponEntry.Detailed -> WeaponHitWidget.draw(
                            content,
                            "    ${weapon.weaponName}",
                            weapon.targetDiceRoll,
                            weapon.successChance,
                            weapon.modifiers,
                            contentColor,
                        )
                        // Enemy attacker: name the weapon that's pointed at us (observable),
                        // but print no target number, hit chance, or modifier breakdown —
                        // that math is computed from the attacker's gunnery/heat/sensor
                        // crits. The type carries no such fields; see DeclaredWeaponEntry.
                        is DeclaredWeaponEntry.Undisclosed ->
                            content.writeln("    ${weapon.weaponName}", Cell.Style(contentColor))
                    }
                }
            }

            // Blank line between attackers, but only if there's a next entry
            if (index < data.entries.size - 1) {
                content.newLine()
            }
        }
    }

}
