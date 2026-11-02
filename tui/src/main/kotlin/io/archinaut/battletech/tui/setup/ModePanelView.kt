package io.archinaut.battletech.tui.setup

import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.widget.RowWriter
import io.archinaut.tenter.widget.CheckState
import io.archinaut.tenter.widget.SelectableRow
import io.archinaut.battletech.tui.icon.TUI_CHECKBOX_GLYPHS

/** Panel 1 (D19/D4/D5): the mode picker while unlocked, then the chosen mode plus host details. */
internal class ModePanelView(
    private val mode: SetupMode,
    private val modeLocked: Boolean,
    private val endpoint: HostEndpoint?,
    private val opponentConnected: Boolean,
    private val cursorIndex: Int,
    private val compact: Boolean = false,
) : MeasurableWidget {

    override fun write(writer: RowWriter) {
        if (!modeLocked || compact) {
            for ((index, candidate) in SetupMode.entries.withIndex()) {
                writer.draw(
                    SelectableRow(
                        label = label(candidate),
                        checkState = if (candidate == mode) CheckState.CHECKED else CheckState.UNCHECKED,
                        selected = index == cursorIndex,
                        glyphs = TUI_CHECKBOX_GLYPHS,
                    ),
                )
                if (!compact) {
                    writer.writeLine(
                        "    ${description(candidate)}",
                        Style(if (index == cursorIndex) StandardRole.ACCENT else StandardRole.TEXT_PRIMARY),
                    )
                }
            }
            return
        }

        writer.draw(
            SelectableRow(
                label = label(mode),
                checkState = CheckState.CHECKED,
                selected = false,
                glyphs = TUI_CHECKBOX_GLYPHS,
            ),
        )

        val ep = endpoint
        if (mode == SetupMode.HOST && ep != null) {
            writer.newLine()
            writer.writeLine("Session: ${ep.sessionId}", TEXT_PRIMARY_STYLE)
            writer.writeLine("Port: ${ep.port}", TEXT_PRIMARY_STYLE)
            writer.newLine()
            for (command in ep.joinCommands) {
                writer.writeLine(command, TEXT_PRIMARY_STYLE)
            }
            writer.newLine()
            writer.writeLine(if (opponentConnected) "Player 2: connected" else "Player 2: waiting…", TEXT_PRIMARY_STYLE)
        }
    }

    private fun description(candidate: SetupMode): String = when (candidate) {
        SetupMode.HOT_SEAT -> "Both players share this terminal"
        SetupMode.HOST -> "Other players connect with 'join'"
    }

    internal companion object {
        internal fun label(candidate: SetupMode): String = when (candidate) {
            SetupMode.HOT_SEAT -> "hot-seat"
            SetupMode.HOST -> "host"
        }

        private val TEXT_PRIMARY_STYLE = Style(StandardRole.TEXT_PRIMARY)
    }
}
