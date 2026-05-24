package battletech.tui.view

import battletech.tactical.model.PlayerId
import battletech.tui.game.phase.DeclaredTargetsRender
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public class DeclaredTargetsView(private val data: DeclaredTargetsRender) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "DECLARED TARGETS")

        val cx = x + 2
        var cy = y + 2
        val inner = width - 4
        val lastRow = y + height - 1

        if (data.entries.isEmpty()) {
            buffer.writeString(cx, cy, "No declarations", Color.WHITE)
            return
        }

        var entriesStarted = 0

        for ((index, entry) in data.entries.withIndex()) {
            if (cy >= lastRow) break

            entriesStarted++
            val attackerColor = if (entry.isDraft) Color.GRAY else playerColor(entry.ownerPlayer)
            val contentColor = if (entry.isDraft) Color.GRAY else Color.WHITE

            buffer.writeString(cx, cy, entry.attackerName.take(inner), attackerColor)
            cy++

            for (target in entry.targets) {
                if (cy >= lastRow) break

                val tag = if (target.isPrimary) "[P]" else "[S]"
                val targetLine = "  > ${target.targetName} $tag"
                buffer.writeString(cx, cy, targetLine.take(inner), contentColor)
                cy++

                for (weapon in target.weapons) {
                    if (cy >= lastRow) break

                    val left = "      ${weapon.weaponName}"
                    val right = "${weapon.successChance}%"
                    val padding = (inner - left.length - right.length).coerceAtLeast(1)
                    val weaponLine = "$left${" ".repeat(padding)}$right"
                    buffer.writeString(cx, cy, weaponLine.take(inner), contentColor)
                    cy++
                }
            }

            // Blank line between attackers, but only if there's a next entry
            if (index < data.entries.size - 1 && cy < lastRow) {
                cy++
            }
        }

        val remaining = data.entries.size - entriesStarted
        if (remaining > 0) {
            val indicatorRow = if (cy < lastRow) cy else lastRow - 1
            buffer.writeString(cx, indicatorRow, "  +$remaining more", Color.WHITE)
        }
    }

    private fun playerColor(player: PlayerId): Color = when (player) {
        PlayerId.PLAYER_1 -> Color.BLUE
        PlayerId.PLAYER_2 -> Color.MAGENTA
    }
}
