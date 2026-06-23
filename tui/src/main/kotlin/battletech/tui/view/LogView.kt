package battletech.tui.view

import battletech.tactical.model.GameState
import battletech.tactical.session.LogEntry
import battletech.tui.game.PanelId
import battletech.tui.screen.ContentWriter
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
        val content = ContentWriter(buffer, x, y, width)
        var lastTurn: Int? = null

        for (entry in entries) {
            val text = GameLogFormatter.format(entry.event, gameState) ?: continue

            if (entry.turn != lastTurn) {
                content.writeHeader("TURN ${entry.turn}")
                lastTurn = entry.turn
            }

            for (line in TextWrap.wrap(text, width)) {
                content.writeln(line)
            }
        }
    }
}
