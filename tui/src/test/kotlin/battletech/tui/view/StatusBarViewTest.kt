package battletech.tui.view

import battletech.tui.screen.ScreenBuffer
import battletech.tactical.action.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class StatusBarViewTest {

    @Test
    fun `renders phase name`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Select destination")
        val buffer = ScreenBuffer(50, 5)

        view.render(buffer, 0, 0, 50, 5)

        val line = (1 until 11).map { buffer.get(it, 1).char }.joinToString("")
        assertEquals("[MOVEMENT]", line)
    }

    @Test
    fun `renders prompt text`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Select destination")
        val buffer = ScreenBuffer(50, 5)

        view.render(buffer, 0, 0, 50, 5)

        val line = (1 until 40).map { buffer.get(it, 2).char }.joinToString("")
        assert(line.contains("Select destination"))
    }

    @Test
    fun `renders keybinding hints`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Select destination")
        val buffer = ScreenBuffer(62, 5)

        view.render(buffer, 0, 0, 62, 5)

        val line = (1 until 58).map { buffer.get(it, 3).char }.joinToString("")
        assert(line.contains("Arrow keys"))
        assert(line.contains("Enter"))
        assert(line.contains("Esc"))
    }

    @Test
    fun `renders box border`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Select destination")
        val buffer = ScreenBuffer(50, 5)

        view.render(buffer, 0, 0, 50, 5)

        assertEquals('╭', buffer.get(0, 0).char)
        assertEquals('╮', buffer.get(49, 0).char)
        assertEquals('╰', buffer.get(0, 4).char)
        assertEquals('╯', buffer.get(49, 4).char)
    }
}
