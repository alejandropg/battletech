package battletech.tui.view

import battletech.tactical.model.TurnPhase
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public class StatusBarView(
    private val phase: TurnPhase,
    private val prompt: String,
    private val activePlayerInfo: String? = null,
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "COMMAND")

        val cx = x + 2
        val cy = y + 2
        val phaseLabel = if (activePlayerInfo != null) {
            "[${phase.name}] $activePlayerInfo"
        } else {
            "[${phase.name}]"
        }
        buffer.writeString(cx, cy, phaseLabel, Cell.Style(Color.BRIGHT_YELLOW))
        buffer.writeString(cx, cy + 1, prompt, Cell.Style(Color.WHITE))
        buffer.writeString(cx, cy + 2, "Arrow keys: move/twist | Enter: confirm | Esc: back | Tab: cycle | c: commit | ctrl+c: quit", Cell.Style(Color.WHITE))
    }
}
