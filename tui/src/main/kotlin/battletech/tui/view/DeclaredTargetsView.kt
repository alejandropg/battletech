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
        private const val PADDING = 2
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "DECLARED TARGETS", index = INDEX)

        val lastRow = y + height - 1
        val content = ContentWriter(buffer, x + PADDING, y + PADDING, width - (PADDING * 2))

        if (data.entries.isEmpty()) {
            content.writeln("No declarations", Color.WHITE)
            return
        }

        var entriesStarted = 0

        for ((index, entry) in data.entries.withIndex()) {
            if (content.cy >= lastRow) break

            entriesStarted++
            val attackerColor = if (entry.isDraft) Color.GRAY else playerColor(entry.ownerPlayer)
            val contentColor = if (entry.isDraft) Color.GRAY else Color.WHITE

            content.writeln(entry.attackerName.take(content.width), attackerColor)

            for (target in entry.targets) {
                if (content.cy >= lastRow) break

                val tag = if (target.isPrimary) "[P]" else "[S]"
                val targetLine = "  > ${target.targetName} $tag"
                content.writeln(targetLine.take(content.width), contentColor)

                for (weapon in target.weapons) {
                    if (content.cy >= lastRow) break

                    val left = "      ${weapon.weaponName}"
                    val right = "${weapon.successChance}%"
                    val padding = (content.width - left.length - right.length).coerceAtLeast(1)
                    val weaponLine = "$left${" ".repeat(padding)}$right"
                    content.writeln(weaponLine.take(content.width), contentColor)
                }
            }

            // Blank line between attackers, but only if there's a next entry
            if (index < data.entries.size - 1 && content.cy < lastRow) {
                content.newLine()
            }
        }

        val remaining = data.entries.size - entriesStarted
        if (remaining > 0) {
            val indicatorRow = if (content.cy < lastRow) content.cy else lastRow - 1
            buffer.writeString(x + 2, indicatorRow, "  +$remaining more", Color.WHITE)
        }
    }

    private fun playerColor(player: PlayerId): Color = when (player) {
        PlayerId.PLAYER_1 -> Color.BLUE
        PlayerId.PLAYER_2 -> Color.MAGENTA
    }
}
