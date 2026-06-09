package battletech.tui.view

import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PanelSlotTest {

    private val realView = object : View {
        override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {}
    }

    @Test
    fun `collapsed slot resolves to a CollapsedPanelView carrying the index and title`() {
        val slot = PanelSlot(AttackResultsView.INDEX, 7, AttackResultsView.TITLE, collapsed = true) { realView }

        val resolved = resolvePanel(slot)

        assertTrue(resolved is CollapsedPanelView) { "Expected CollapsedPanelView, got $resolved" }
        resolved as CollapsedPanelView
        assertEquals(AttackResultsView.INDEX, resolved.index)
        assertEquals(AttackResultsView.TITLE, resolved.title)
    }

    @Test
    fun `expanded slot resolves to the real view`() {
        val slot = PanelSlot(AttackResultsView.INDEX, 36, AttackResultsView.TITLE, collapsed = false) { realView }

        assertSame(realView, resolvePanel(slot))
    }

    @Test
    fun `slot with no width resolves to null`() {
        val slot = PanelSlot(AttackResultsView.INDEX, 0, AttackResultsView.TITLE, collapsed = true) { realView }

        assertNull(resolvePanel(slot))
    }

    @Test
    fun `buildReal is not invoked for a collapsed slot`() {
        var built = false
        val slot = PanelSlot(AttackResultsView.INDEX, 7, AttackResultsView.TITLE, collapsed = true) {
            built = true
            realView
        }

        resolvePanel(slot)

        assertTrue(!built) { "buildReal must not run when the panel is collapsed" }
    }
}
