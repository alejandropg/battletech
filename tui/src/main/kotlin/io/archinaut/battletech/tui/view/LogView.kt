package io.archinaut.battletech.tui.view

import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.query.PlayerGameState
import io.archinaut.battletech.tactical.session.LogEntry
import io.archinaut.tenter.screen.Constraint
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.Paragraph
import io.archinaut.tenter.widget.Row
import io.archinaut.tenter.widget.RowWriter
import io.archinaut.tenter.widget.followingEnd
import io.archinaut.tenter.widget.panel.Panel

/**
 * The game log, oldest entry first. It follows its own end, so new entries stay in view while the
 * reader is at the bottom of its [Panel], and a reader who scrolled up to review history stays
 * there until they scroll back down.
 */
internal class LogView(
    private val entries: List<LogEntry>,
    private val state: PlayerGameState,
) : MeasurableWidget {

    override fun write(writer: RowWriter) {
        writer.draw(MeasurableWidget(::writeEntries).followingEnd())
    }

    private fun writeEntries(writer: RowWriter) {
        var lastTurn: Int? = null

        for (entry in entries) {
            val logLines = GameLogFormatter.lines(entry.event, state)
            if (logLines.isEmpty()) continue

            if (entry.turn != lastTurn) {
                writer.draw(SectionHeading("TURN ${entry.turn}"))
                lastTurn = entry.turn
            }

            for (line in logLines) {
                // The icon gets its own column so wrapped text hangs indented under the first row.
                writer.draw(
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
