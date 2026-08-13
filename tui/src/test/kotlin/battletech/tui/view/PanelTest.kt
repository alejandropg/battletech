package battletech.tui.view

import battletech.tactical.model.HexCoordinates
import battletech.tui.aGameState
import battletech.tui.game.AppState
import battletech.tui.game.PanelId
import battletech.tui.game.phase.MovementPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.panel.Panel
import tenter.screen.Canvas
import tenter.screen.ScreenBuffer
import tenter.view.View
import tenter.view.render
import tenter.view.text

/**
 * [Panel] owns its own collapsed state, scroll offset, and auto-follow focus across renders — see
 * its KDoc; this is what deleted the scroll round trip through `AppState` that `RunLoop.syncScroll`
 * used to do. These tests exercise that persistence directly against a bare [Panel], with no
 * [Workspace], `AppState` mutation, or real game content in the way.
 */
internal class PanelTest {

    // build lambdas below ignore their PanelInputs argument, so this stand-in never needs to be a
    // realistic frame — only a valid one to pass through.
    private val inputs = PanelInputs(AppState(gameState = aGameState(), phase = MovementPhase.SelectingUnit, cursor = HexCoordinates(0, 0)))

    private fun stubContent(lines: Int): View = object : View {
        override fun render(canvas: Canvas) {
            for (i in 0 until lines) canvas.writeString(0, i, "row$i")
        }
    }

    private fun renderPanel(panel: GamePanel, width: Int = 30, height: Int = 10, forgetFocus: Boolean = false): ScreenBuffer {
        val buffer = ScreenBuffer(width, height)
        panel.render(Canvas.of(buffer), inputs, forgetFocus)
        return buffer
    }

    @Test
    fun `starts expanded at its expandedWidth`() {
        val panel: GamePanel = Panel(PanelId.LOG, "T", expandedWidth = 28) { stubContent(3) }

        assertEquals(28, panel.width)
        assertFalse(panel.collapsed)
    }

    @Test
    fun `toggleCollapsed shrinks to the stub width and back`() {
        val panel: GamePanel = Panel(PanelId.LOG, "T", expandedWidth = 28) { stubContent(3) }

        panel.toggleCollapsed()
        assertEquals(Panel.COLLAPSED_WIDTH, panel.width)
        assertTrue(panel.collapsed)

        panel.toggleCollapsed()
        assertEquals(28, panel.width)
        assertFalse(panel.collapsed)
    }

    @Test
    fun `collapsed state persists across renders with no external round trip`() {
        val panel: GamePanel = Panel(PanelId.LOG, "T", expandedWidth = 28) { stubContent(3) }
        panel.toggleCollapsed()

        renderPanel(panel)
        renderPanel(panel)

        assertTrue(panel.collapsed, "collapse must survive repeated renders on its own")
    }

    @Test
    fun `a collapsed panel never calls build`() {
        var built = false
        val panel: GamePanel = Panel(PanelId.LOG, "T", expandedWidth = 28) {
            built = true
            stubContent(3)
        }
        panel.toggleCollapsed()

        renderPanel(panel)

        assertFalse(built, "Panel.build must not run when the panel is collapsed")
    }

    @Test
    fun `an expanded panel whose build returns null renders nothing`() {
        val panel: GamePanel = Panel(PanelId.LOG, "T", expandedWidth = 28) { null }

        val buffer = renderPanel(panel)

        assertTrue(buffer.text().isBlank(), "no box, no content — build declined")
    }

    @Test
    fun `scroll offset persists across renders with no explicit re-scroll`() {
        fun freshPanel(): GamePanel = Panel(PanelId.LOG, "T", expandedWidth = 30) { stubContent(20) }

        val unscrolled = renderPanel(freshPanel())

        val panel = freshPanel()
        panel.scrollBy(3)
        val firstAfterScroll = renderPanel(panel)
        val secondAfterScroll = renderPanel(panel) // no scrollBy call between renders

        assertNotEquals(unscrolled.text(), firstAfterScroll.text(), "sanity check: the scroll actually moved content")
        assertEquals(firstAfterScroll.text(), secondAfterScroll.text(), "offset must survive a render with no further scroll")
    }

    @Test
    fun `forgetFocus re-follows even though the marked focus has not moved`() {
        val focused: View = object : View {
            override fun render(canvas: Canvas) {
                for (i in 0 until 20) canvas.writeString(0, i, "row$i")
                canvas.markFocus(0, 15, canvas.width, 1)
            }
        }
        val panel: GamePanel = Panel(PanelId.LOG, "T", expandedWidth = 30) { focused }

        renderPanel(panel) // first render follows the focus into view
        panel.scrollBy(-100) // manual scroll away; clamped to 0 on the next render

        // Without forgetFocus, the focus hasn't moved since last render, so the manual scroll sticks.
        val stayedAway = renderPanel(panel)
        // forgetFocus is a one-shot override: treat this render's focus as freshly arrived.
        val forced = renderPanel(panel, forgetFocus = true)

        assertNotEquals(stayedAway.text(), forced.text(), "forgetFocus should re-follow even though focus itself didn't move")
    }
}
