package battletech.tui.view

import battletech.tactical.query.PlayerGameState
import battletech.tactical.session.LogEntry
import battletech.tui.screen.Canvas
import battletech.tui.screen.CellWidth
import battletech.tui.screen.ContentWriter
import battletech.tui.screen.TextWrap

/**
 * Marks its last written row as focus, so the enclosing [ScrollableView] follows new entries to
 * the bottom exactly as [TargetsView]'s cursor row does — the same mechanism, not a bespoke
 * bottom-anchor. A consequence: a new entry always scrolls to the bottom, even if the reader had
 * scrolled up to review history.
 */
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

        if (content.row > 0) content.markFocusAt(content.row - 1)
    }

    internal companion object {
        internal const val TITLE: String = "LOG"
    }
}
