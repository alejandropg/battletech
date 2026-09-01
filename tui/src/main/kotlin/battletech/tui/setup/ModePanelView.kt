package battletech.tui.setup

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.CheckState
import tenter.widget.Checkbox

/** Panel 1 (D19/D4/D5): the mode picker while unlocked, then the chosen mode plus host details. */
internal class ModePanelView(
    private val mode: SetupMode,
    private val modeLocked: Boolean,
    private val endpoint: HostEndpoint?,
    private val opponentConnected: Boolean,
) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)

        if (!modeLocked) {
            for (candidate in SetupMode.entries) {
                val state = if (candidate == mode) CheckState.CHECKED else CheckState.UNCHECKED
                val row = content.writeLine("    ${label(candidate)}", TEXT_PRIMARY_STYLE)
                Checkbox.draw(content, 2, row, state)
            }
            return
        }

        val row = content.writeLine("    ${label(mode)}", TEXT_PRIMARY_STYLE)
        Checkbox.draw(content, 2, row, CheckState.CHECKED)

        val ep = endpoint
        if (mode == SetupMode.HOST && ep != null) {
            content.newLine()
            content.writeLine("Session: ${ep.sessionId}", TEXT_PRIMARY_STYLE)
            content.writeLine("Port: ${ep.port}", TEXT_PRIMARY_STYLE)
            content.newLine()
            for (address in ep.addresses) {
                content.writeLine("join $address:${ep.port} --session ${ep.sessionId}", TEXT_PRIMARY_STYLE)
            }
            content.newLine()
            content.writeLine(if (opponentConnected) "Player 2: connected" else "Player 2: waiting…", TEXT_PRIMARY_STYLE)
        }
    }

    private fun label(candidate: SetupMode): String = when (candidate) {
        SetupMode.HOT_SEAT -> "hot-seat"
        SetupMode.HOST -> "host"
    }

    private companion object {
        val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    }
}
