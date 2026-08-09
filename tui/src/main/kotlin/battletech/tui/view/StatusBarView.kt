package battletech.tui.view

import battletech.tactical.model.TurnPhase
import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color

public class StatusBarView(
    private val phase: TurnPhase,
    private val prompt: String,
    private val activePlayerInfo: String? = null,
) : View {

    override fun render(canvas: Canvas) {
        val content = PanelChrome.draw(canvas, "COMMAND", padding = PanelChrome.STATUS_BAR_PADDING)

        val phaseLabel = if (activePlayerInfo != null) {
            "[${phase.name}] $activePlayerInfo"
        } else {
            "[${phase.name}]"
        }
        content.writeString(0, 0, phaseLabel, BRIGHT_YELLOW_STYLE)
        content.writeString(0, 1, prompt, WHITE_STYLE)
    }

    private companion object {
        private val BRIGHT_YELLOW_STYLE = Cell.Style(Color.BRIGHT_YELLOW)
        private val WHITE_STYLE = Cell.Style(Color.WHITE)
    }
}
