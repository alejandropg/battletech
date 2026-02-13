package battletech.tui.view

import battletech.tui.screen.ScreenBuffer
import battletech.tactical.action.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class StatusBarViewTest {

    @Test
    fun `renders phase name`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Select destination")
        val buffer = ScreenBuffer(50, 3)

        view.render(buffer, 0, 0, 50, 3)

        val line = (0 until 10).map { buffer.get(it, 0).char }.joinToString("")
        assertEquals("[MOVEMENT]", line)
    }

    @Test
    fun `renders prompt text`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Select destination")
        val buffer = ScreenBuffer(50, 3)

        view.render(buffer, 0, 0, 50, 3)

        val line = (0 until 40).map { buffer.get(it, 1).char }.joinToString("")
        assert(line.contains("Select destination"))
    }

    @Test
    fun `renders keybinding hints`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Select destination")
        val buffer = ScreenBuffer(60, 3)

        view.render(buffer, 0, 0, 60, 3)

        val line = (0 until 60).map { buffer.get(it, 2).char }.joinToString("")
        assert(line.contains("Arrow keys"))
        assert(line.contains("Enter"))
        assert(line.contains("Esc"))
    }
}
