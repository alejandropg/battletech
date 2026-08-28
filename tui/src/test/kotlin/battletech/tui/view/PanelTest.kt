package battletech.tui.view

import battletech.tactical.model.HexCoordinates
import battletech.tui.aGameState
import battletech.tui.game.AppState
import battletech.tui.game.GamePanelId
import battletech.tui.game.phase.MovementPhase
import battletech.tui.input.Keybindings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import tenter.panel.Panel
import tenter.panel.PanelState
import tenter.panel.VerticalTitleView
import tenter.screen.Canvas
import tenter.screen.ChromeRole
import tenter.screen.ScreenBuffer
import tenter.view.View
import tenter.view.text

/**
 * [Panel] owns its own state (minimized/normal/maximized), scroll offset, and auto-follow reveal
 * across renders — see its KDoc; this is what deleted the scroll round trip through `AppState`
 * that `RunLoop.syncScroll` used to do. These tests exercise that persistence directly against a
 * bare [Panel], with no [Workspace], `AppState` mutation, or real game content in the way.
 */
internal class PanelTest {

    // build lambdas below ignore their PanelInputs argument, so this stand-in never needs to be a
    // realistic frame — only a valid one to pass through.
    private val inputs = PanelInputs(
        AppState(gameState = aGameState(), phase = MovementPhase.SelectingUnit, cursor = HexCoordinates(0, 0)),
        Keybindings.DEFAULT,
    )

    private fun stubContent(lines: Int): View = object : View {
        override fun draw(canvas: Canvas) {
            for (i in 0 until lines) canvas.writeString(0, i, "row$i")
        }
    }

    private fun renderPanel(
        panel: GamePanel,
        width: Int = 30,
        height: Int = 10,
        focused: Boolean = false,
        forgetReveal: Boolean = false,
    ): ScreenBuffer {
        val buffer = ScreenBuffer(width, height)
        panel.render(Canvas.of(buffer), inputs, focused = focused, forgetReveal = forgetReveal)
        return buffer
    }

    @Test
    fun `starts at NORMAL, at its normalWidth`() {
        val panel: GamePanel = Panel(GamePanelId.LOG, "T", normalWidth = 28, normal = { stubContent(3) })

        assertEquals(28, panel.width)
        assertEquals(PanelState.NORMAL, panel.state)
    }

    @Test
    fun `renders its chrome even when the content view draws nothing`() {
        // A builder always returns a view (see Panel's KDoc — "nothing to show" is a visibility
        // decision, not a builder's), so an empty view still gets a bordered, badged panel.
        val panel: GamePanel = Panel(GamePanelId.LOG, "T", normalWidth = 28, badge = '9', normal = { EMPTY_VIEW })

        val buffer = renderPanel(panel)

        assertEquals("╭", buffer.get(0, 0).char, "the border is drawn regardless of what the content view paints")
        assertEquals("9", buffer.get(3, 0).char, "the badge identifies the panel")
    }

    @Test
    fun `scroll offset persists across renders with no explicit re-scroll`() {
        fun freshPanel(): GamePanel = Panel(GamePanelId.LOG, "T", normalWidth = 30, normal = { stubContent(20) })

        val unscrolled = renderPanel(freshPanel())

        val panel = freshPanel()
        panel.scrollBy(0, 3)
        val firstAfterScroll = renderPanel(panel)
        val secondAfterScroll = renderPanel(panel) // no scrollBy call between renders

        assertNotEquals(unscrolled.text(), firstAfterScroll.text(), "sanity check: the scroll actually moved content")
        assertEquals(firstAfterScroll.text(), secondAfterScroll.text(), "offset must survive a render with no further scroll")
    }

    @Test
    fun `forgetReveal re-follows even though the marked reveal target has not moved`() {
        val revealing: View = object : View {
            override fun draw(canvas: Canvas) {
                for (i in 0 until 20) canvas.writeString(0, i, "row$i")
                canvas.markReveal(0, 15, canvas.width, 1)
            }
        }
        val panel: GamePanel = Panel(GamePanelId.LOG, "T", normalWidth = 30, normal = { revealing })

        renderPanel(panel) // first render follows the reveal target into view
        panel.scrollBy(0, -100) // manual scroll away; clamped to 0 on the next render

        // Without forgetReveal, the reveal target hasn't moved since last render, so the manual scroll sticks.
        val stayedAway = renderPanel(panel)
        // forgetReveal is a one-shot override: treat this render's reveal target as freshly arrived.
        val forced = renderPanel(panel, forgetReveal = true)

        assertNotEquals(stayedAway.text(), forced.text(), "forgetReveal should re-follow even though the reveal target itself didn't move")
    }

    @Test
    fun `focused renders the border and title in the focus color, unfocused renders neutral`() {
        val panel: GamePanel = Panel(GamePanelId.LOG, "T", normalWidth = 30, normal = { stubContent(3) })

        val focused = renderPanel(panel, focused = true)
        assertEquals(ChromeRole.PANEL_BORDER_FOCUSED, focused.get(0, 0).style.fg, "border corner")
        assertEquals(ChromeRole.PANEL_BORDER_FOCUSED, focused.get(2, 0).style.fg, "badge/title run")

        val unfocused = renderPanel(panel)
        assertEquals(ChromeRole.PANEL_BORDER, unfocused.get(0, 0).style.fg, "border corner")
        assertEquals(ChromeRole.PANEL_BORDER, unfocused.get(2, 0).style.fg, "badge/title run")
    }

    @Test
    fun `a minimized panel keeps its badge in the border and never builds its NORMAL view`() {
        var normalBuilds = 0
        val panel: GamePanel = Panel(
            GamePanelId.LOG,
            LOG_TITLE,
            normalWidth = 28,
            badge = '9',
            normal = {
                normalBuilds++
                stubContent(3)
            },
            minimized = { VerticalTitleView(LOG_TITLE) },
        )
        panel.cycleState(-1) // NORMAL -> MINIMIZED

        val buffer = renderPanel(panel, width = Panel.MINIMIZED_WIDTH)

        // The full "[badge] title" run can't fit in a 7-column stub, so Bordered falls back to the
        // badge alone — the one thing that still identifies which panel this stub is (Alt+9 here).
        assertEquals("[", buffer.get(2, 0).char)
        assertEquals("9", buffer.get(3, 0).char)
        assertEquals("]", buffer.get(4, 0).char)

        // Title runs vertically down the stub's 3-column content area: border(1) + gutter(1) puts
        // its center at column 3, and the reclaimable top padding row puts its first letter at row 2.
        assertEquals("L", buffer.get(3, 2).char)
        assertEquals("O", buffer.get(3, 3).char)
        assertEquals("G", buffer.get(3, 4).char)

        assertEquals(0, normalBuilds, "the NORMAL builder must not run while the panel is minimized")
    }

    @Test
    fun `a focused panel's scrollbar thumb also renders in the focus color`() {
        val panel: GamePanel = Panel(GamePanelId.LOG, "T", normalWidth = 30, normal = { stubContent(20) })

        val buffer = renderPanel(panel, focused = true)

        val thumbRow = (1..8).first { buffer.get(29, it).char == "▐" }
        assertEquals(ChromeRole.PANEL_BORDER_FOCUSED, buffer.get(29, thumbRow).style.fg)
    }

    private companion object {
        /** A three-letter title, so the vertical stub's letters land on known rows. */
        private const val LOG_TITLE = "LOG"

        /** A valid view that paints nothing — the closest thing to the old "build declined" case. */
        private val EMPTY_VIEW = object : View {
            override fun draw(canvas: Canvas) = Unit
        }
    }
}
