package battletech.tui.view

import battletech.tactical.model.GameState
import battletech.tactical.session.LogEntry
import battletech.tui.game.PanelId
import battletech.tui.screen.CellWidth
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
            val logLines = GameLogFormatter.lines(entry.event, gameState)
            if (logLines.isEmpty()) continue

            if (entry.turn != lastTurn) {
                content.writeHeader("TURN ${entry.turn}")
                lastTurn = entry.turn
            }

            for (line in logLines) {
                val icon = line.icon ?: ">"
                val prefixWidth = CellWidth.of(icon) + 1
                val indent = " ".repeat(prefixWidth)

                TextWrap.wrap(line.text, width - prefixWidth, width - prefixWidth).forEachIndexed { i, wrapped ->
                    content.writeln(if (i == 0) "$icon $wrapped" else "$indent$wrapped")
                }
            }
        }
    }
}
