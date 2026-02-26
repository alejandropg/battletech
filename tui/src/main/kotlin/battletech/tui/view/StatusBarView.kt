package battletech.tui.view

import battletech.tactical.action.TurnPhase
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
        buffer.writeString(cx, cy, phaseLabel, Color.BRIGHT_YELLOW)
        buffer.writeString(cx, cy + 1, prompt, Color.WHITE)
        buffer.writeString(cx, cy + 2, "Arrow keys: move | Enter: confirm | Esc: back | Tab: cycle | q: quit", Color.WHITE)
    }
}
