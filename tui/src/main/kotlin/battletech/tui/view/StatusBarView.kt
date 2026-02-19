package battletech.tui.view

import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import battletech.tactical.action.TurnPhase

public class StatusBarView(
    private val phase: TurnPhase,
    private val prompt: String,
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "COMMAND")

        val cx = x + 1
        val cy = y + 1
        buffer.writeString(cx, cy, "[${phase.name}]", Color.BRIGHT_YELLOW)
        buffer.writeString(cx, cy + 1, prompt, Color.WHITE)
        buffer.writeString(cx, cy + 2, "Arrow keys: move | Enter: confirm | Esc: back | q: quit", Color.WHITE)
    }
}
