package io.archinaut.battletech.tui.view

import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.query.PlayerGameState
import io.archinaut.battletech.tactical.session.LogEntry
import io.archinaut.tenter.screen.Constraint
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.Paragraph
import io.archinaut.tenter.widget.Row
import io.archinaut.tenter.widget.RowWriter
import io.archinaut.tenter.widget.panel.Panel

/**
 * The game log, oldest entry first. It makes no reveal request: its [Panel] sticks to the bottom,
 * so new entries stay in view while the reader is at the bottom, and a reader who scrolled up to
 * review history stays there until they scroll back down.
 */
internal class LogView(
    private val entries: List<LogEntry>,
    private val state: PlayerGameState,
) : MeasurableWidget {

    override fun write(content: RowWriter) {
        var lastTurn: Int? = null

        for (entry in entries) {
            val logLines = GameLogFormatter.lines(entry.event, state)
            if (logLines.isEmpty()) continue

            if (entry.turn != lastTurn) {
                content.draw(SectionHeading("TURN ${entry.turn}"))
                lastTurn = entry.turn
            }

            for (line in logLines) {
                // The icon gets its own column so wrapped text hangs indented under the first row.
                content.draw(
                    Row(
                        listOf(
                            Constraint.Length(CellWidth.of(line.icon) + 1) to Paragraph(line.icon),
                            Constraint.Fill() to Paragraph(listOf(line.content), wrap = true),
                        ),
                    ),
                )
            }
        }
    }

    internal companion object {
        internal const val TITLE: String = "LOG"
    }
}
