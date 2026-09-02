package battletech.tui.setup

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.CheckState
import tenter.widget.SelectableRow

/** Panel 1 (D19/D4/D5): the mode picker while unlocked, then the chosen mode plus host details. */
internal class ModePanelView(
    private val mode: SetupMode,
    private val modeLocked: Boolean,
    private val endpoint: HostEndpoint?,
    private val opponentConnected: Boolean,
    private val cursorIndex: Int,
) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)

        if (!modeLocked) {
            for ((index, candidate) in SetupMode.entries.withIndex()) {
                SelectableRow.draw(
                    content = content,
                    label = label(candidate),
                    checkState = if (candidate == mode) CheckState.CHECKED else CheckState.UNCHECKED,
                    cursor = index == cursorIndex,
                )
            }
            return
        }

        SelectableRow.draw(
            content = content,
            label = label(mode),
            checkState = CheckState.CHECKED,
            cursor = false,
        )

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
