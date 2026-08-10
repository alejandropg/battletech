package battletech.tui.view

import battletech.tactical.model.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class StatusBarViewTest {

    @Test
    fun `renders phase name`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Select destination")

        val buffer = render(view, 50, 7)

        val line = (2 until 12).joinToString("") { buffer.get(it, 1).char }
        assertEquals("[MOVEMENT]", line)
    }

    @Test
    fun `renders prompt text`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Select destination")

        val buffer = render(view, 50, 7)

        val line = (2 until 40).joinToString("") { buffer.get(it, 2).char }
        assert(line.contains("Select destination"))
    }

    @Test
    fun `renders box border`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Select destination")

        val buffer = render(view, 50, 7)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╮", buffer.get(49, 0).char)
        assertEquals("╰", buffer.get(0, 6).char)
        assertEquals("╯", buffer.get(49, 6).char)
    }

    @Test
    fun `at production height, label and prompt sit flush against the borders with no clipping`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Select destination")

        val buffer = render(view, 50, Workspace.STATUS_BAR_HEIGHT)

        assertEquals("╭", buffer.get(0, 0).char)
        val phaseLabel = (2 until 12).joinToString("") { buffer.get(it, 1).char }
        assertEquals("[MOVEMENT]", phaseLabel)
        val prompt = (2 until 40).joinToString("") { buffer.get(it, 2).char }
        assert(prompt.contains("Select destination"))
        assertEquals("╰", buffer.get(0, 3).char)
    }

    @Test
    fun `a prompt longer than the panel truncates at the right gutter instead of overwriting the border`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "x".repeat(100))

        val buffer = render(view, 50, 7)

        assertEquals("╮", buffer.get(49, 0).char)
        assertEquals("│", buffer.get(49, 2).char)
    }
}
