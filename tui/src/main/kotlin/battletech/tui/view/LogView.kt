package battletech.tui.view

import battletech.tactical.query.PlayerGameState
import battletech.tactical.session.LogEntry
import battletech.tui.screen.Canvas
import battletech.tui.screen.CellWidth
import battletech.tui.screen.ContentWriter
import battletech.tui.screen.TextWrap

public class LogView(
    private val entries: List<LogEntry>,
    private val state: PlayerGameState,
) : View {

    override fun render(canvas: Canvas) {
        val content = ContentWriter(canvas)
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

                TextWrap.wrap(line.text, content.width - prefixWidth, content.width - prefixWidth).forEachIndexed { i, wrapped ->
                    content.writeln(if (i == 0) "$icon $wrapped" else "$indent$wrapped")
                }
            }
        }
    }

    internal companion object {
        internal const val TITLE: String = "LOG"
    }
}
