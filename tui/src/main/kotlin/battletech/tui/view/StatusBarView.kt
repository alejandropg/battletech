package battletech.tui.view

import battletech.tactical.model.TurnPhase
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.Insets
import tenter.screen.UiRole
import tenter.view.Bordered
import tenter.view.View

internal class StatusBarView(
    private val phase: TurnPhase,
    private val prompt: String,
    private val activePlayerInfo: String? = null,
) : View {

    override fun render(canvas: Canvas) {
        Bordered(title = "COMMAND", gutters = STATUS_BAR_PADDING, content = Content()).render(canvas)
    }

    private inner class Content : View {
        override fun render(canvas: Canvas) {
            val phaseLabel = if (activePlayerInfo != null) {
                "[${phase.name}] $activePlayerInfo"
            } else {
                "[${phase.name}]"
            }
            canvas.writeString(0, 0, phaseLabel, ACCENT_STYLE)
            canvas.writeString(0, 1, prompt, TEXT_PRIMARY_STYLE)
        }
    }

    private companion object {
        private val ACCENT_STYLE = Cell.Style(UiRole.ACCENT)
        private val TEXT_PRIMARY_STYLE = Cell.Style(UiRole.TEXT_PRIMARY)

        /**
         * The status bar is only [Workspace.STATUS_BAR_HEIGHT] = 4 rows tall: border alone
         * takes 2, and it needs 2 content rows (phase label + prompt), so it cannot afford
         * [Bordered.PADDING]'s spacer row — `top = 0`.
         */
        private val STATUS_BAR_PADDING = Insets(left = 1, top = 0, right = 1, bottom = 0)
    }
}
