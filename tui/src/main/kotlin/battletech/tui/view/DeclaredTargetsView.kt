package battletech.tui.view

import battletech.tactical.model.PlayerId
import battletech.tui.game.PanelId
import battletech.tui.game.phase.DeclaredTargetsRender
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
            content.writeln("No declarations", Color.WHITE)
            return
        }

        for ((index, entry) in data.entries.withIndex()) {
            val attackerColor = if (entry.isDraft) Color.DRAFT else playerColor(entry.ownerPlayer)
            val contentColor = if (entry.isDraft) Color.DRAFT else Color.WHITE

            content.writeln(entry.attackerName, attackerColor)

            for (target in entry.targets) {
                val tag = if (target.isPrimary) "[P]" else "[S]"
                val targetLine = "> ${target.targetName} $tag"
                content.writeln(targetLine, contentColor)

                for (weapon in target.weapons) {
                    val left = "    ${weapon.weaponName}"
                    val right = "${weapon.successChance}%"
                    val padding = (content.width - left.length - right.length).coerceAtLeast(1)
                    val weaponLine = "$left${" ".repeat(padding)}$right"
                    content.writeln(weaponLine, contentColor)
                }
            }

            // Blank line between attackers, but only if there's a next entry
            if (index < data.entries.size - 1) {
                content.newLine()
            }
        }
    }

    private fun playerColor(player: PlayerId): Color = when (player) {
        PlayerId.PLAYER_1 -> Color.BLUE
        PlayerId.PLAYER_2 -> Color.MAGENTA
    }
}
