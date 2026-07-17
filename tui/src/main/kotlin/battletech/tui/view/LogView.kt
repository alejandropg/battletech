package battletech.tui.view

import battletech.tactical.query.PlayerGameState
import battletech.tactical.session.LogEntry
import battletech.tui.game.PanelId
import battletech.tui.screen.CellWidth
import battletech.tui.screen.ContentWriter
import battletech.tui.screen.ScreenBuffer
import battletech.tui.screen.TextWrap

public class LogView(
    private val entries: List<LogEntry>,
    private val state: PlayerGameState,
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        val content = ContentWriter(buffer, x, y, width)
        var lastTurn: Int? = null

        for (entry in entries) {
            val logLines = GameLogFormatter.lines(entry.event, state)
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

    internal companion object {
        internal val INDEX: Int = PanelId.LOG.index
        internal const val TITLE: String = "LOG"
    }
}
