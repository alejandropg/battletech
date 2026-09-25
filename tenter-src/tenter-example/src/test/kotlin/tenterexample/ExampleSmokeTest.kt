package tenterexample

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import io.archinaut.tenter.panel.Panel
import io.archinaut.tenter.panel.PanelSet

internal class ExampleSmokeTest {

    @Test
    fun `headless consumer renders long list help and animation through public tenter seams`() {
        val result = runHeadlessSmoke()

        assertEquals(600, result.renderedRows)
        assertTrue(result.helpContainsMovement)
        assertTrue(result.animationCompleted)
    }

    @Test
    fun `validated keymap movement and public settled hit test drive selection and toggle`() {
        val app = ExampleApp(ExampleLayoutMode.MAIN_AND_SIDES)
        app.render(width = 80, height = 12)

        app.handleKey(KeyboardEvent("ArrowDown"))
        app.handleKey(KeyboardEvent(" "))

        assertEquals(1, app.state.selectedRow)
        assertTrue(1 in app.state.checkedRows)

        val slot = checkNotNull(checkNotNull(app.lastLayout).main)
        val clickX = slot.content.x + 1
        val clickY = slot.content.y + 3
        val hit = checkNotNull(app.panels.hitTest(clickX, clickY))
        val clickedRow = checkNotNull(hit.contentPoint).y

        app.handle(MouseEvent(x = clickX, y = clickY, left = true))

        assertEquals(clickedRow, app.state.selectedRow)
        assertTrue(clickedRow in app.state.checkedRows)
    }

    @Test
    fun `each layout mode receives fresh panel instances and attachment remains exclusive`() {
        // Both apps coexist and render only because each one builds its own panel instances.
        val mainAndSides = ExampleApp(ExampleLayoutMode.MAIN_AND_SIDES)
        val uniform = ExampleApp(ExampleLayoutMode.UNIFORM)
        mainAndSides.render(width = 80, height = 12)
        uniform.render(width = 80, height = 12)

        assertEquals(ExamplePanelId.ROWS, mainAndSides.panels.focused)
        assertEquals(ExamplePanelId.ROWS, uniform.panels.focused)

        // Sharing one instance across sets is what fails, and it fails at construction.
        val shared = Panel<ExamplePanelId, ExampleState>(
            id = ExamplePanelId.ROWS,
            title = "CATALOG",
            normal = { input -> Panel.Presentation.allocated(ExampleListView(1, input)) },
        )
        PanelSet.uniform(listOf(shared))

        assertThrows<IllegalArgumentException> { PanelSet.uniform(listOf(shared)) }
    }
}
