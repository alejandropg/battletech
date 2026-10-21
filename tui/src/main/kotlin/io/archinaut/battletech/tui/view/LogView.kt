package io.archinaut.battletech.tui.view

import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.query.PlayerGameState
import io.archinaut.battletech.tactical.session.LogEntry
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.Column
import io.archinaut.tenter.widget.Columns
import io.archinaut.tenter.widget.Paragraph
import io.archinaut.tenter.widget.RowWriter
import io.archinaut.tenter.widget.Viewport

/**
 * Marks its last written row for reveal, so the enclosing [Viewport] follows new entries to
 * the bottom exactly as [TargetsView]'s cursor row does — the same mechanism, not a bespoke
 * bottom-anchor. A consequence: a new entry always scrolls to the bottom, even if the reader had
 * scrolled up to review history.
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
                val prefixWidth = CellWidth.of(line.icon) + 1
                val available = (content.width - prefixWidth).coerceAtLeast(1)
                content.draw(
                    Columns(
                        listOf(
                            Column(prefixWidth, Paragraph(line.icon)),
                            Column(available, Paragraph(listOf(line.content), wrap = true)),
                        ),
                        gutter = 0,
                    ),
                )
            }
        }

        if (content.row > 0) content.markReveal(row = content.row - 1)
    }

    internal companion object {
        internal const val TITLE: String = "LOG"
    }
}
