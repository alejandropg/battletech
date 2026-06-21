package battletech.tui.view

import battletech.tactical.model.GameState
import battletech.tactical.session.LogEntry
import battletech.tui.game.PanelId
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import battletech.tui.screen.TextWrap

public class LogView(
    private val entries: List<LogEntry>,
    private val gameState: GameState,
) : View {

    public companion object {
        public val INDEX: Int = PanelId.LOG.index
        public const val TITLE: String = "LOG"
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        var row = 0
        var lastTurn: Int? = null

        for (entry in entries) {
            val text = GameLogFormatter.format(entry.event, gameState) ?: continue

            if (entry.turn != lastTurn) {
                buffer.writeString(x, y + row, turnHeader(entry.turn, width), fg = Color.GRAY)
                row += 1
                lastTurn = entry.turn
            }

            for (line in TextWrap.wrap(text, width)) {
                buffer.writeString(x, y + row, line)
                row += 1
            }
        }
    }

    private fun turnHeader(turn: Int, width: Int): String {
        val label = "── TURN $turn "
        val fill = (width - label.length).coerceAtLeast(0)
        return label + "─".repeat(fill)
    }
}
