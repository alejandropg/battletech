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
import tenter.panel.PanelSet
import tenter.panel.PanelState
import tenter.panel.VerticalTitleView
import tenter.screen.Canvas
import tenter.screen.ChromeRole
import tenter.screen.ScreenBuffer
import tenter.view.ContentView
import tenter.view.contentView
import tenter.view.text

/** Managed panel behavior is exercised through the public [PanelSet] seam. */
internal class PanelTest {

    private val inputs = PanelInputs(
        AppState(gameState = aGameState(), phase = MovementPhase.SelectingUnit, cursor = HexCoordinates(0, 0)),
        Keybindings.DEFAULT,
    )

    private fun stubContent(lines: Int): ContentView = contentView { cursor ->
        repeat(lines) { row -> cursor.writeLine("row$row") }
    }

    private fun panel(
        normal: (PanelInputs) -> ContentView = { stubContent(3) },
        minimized: ((PanelInputs) -> ContentView)? = null,
        maximized: ((PanelInputs) -> ContentView)? = null,
    ): GamePanel = Panel(
        id = GamePanelId.LOG,
        title = "T",
        badge = "9",
        normal = { Panel.Presentation.fixedWidth(normal(it), 28) },
        minimized = minimized?.let { build -> { Panel.Presentation.fixedWidth(build(it), Panel.MINIMIZED_WIDTH) } },
        maximized = maximized?.let { build -> { Panel.Presentation.allocated(build(it)) } },
    )

    private fun renderPanel(
        panel: GamePanel,
        width: Int = 30,
        height: Int = 10,
        configure: (GamePanelSet) -> Unit = {},
    ): ScreenBuffer {
        val set = PanelSet.uniform(listOf(panel))
        configure(set)
        val buffer = ScreenBuffer(width, height)
        set.render(Canvas.of(buffer), inputs, visible = setOf(GamePanelId.LOG), reservedTop = 0)
        return buffer
    }

    private fun render(set: GamePanelSet, width: Int = 30, height: Int = 10): ScreenBuffer {
        val buffer = ScreenBuffer(width, height)
        set.render(Canvas.of(buffer), inputs, visible = set.sides.map { it.id }.toSet(), reservedTop = 0)
        return buffer
    }

    @Test
    fun `presentation width and state are observed through the set`() {
        val panel = panel()
        val set = PanelSet.uniform(listOf(panel))

        assertEquals(PanelState.NORMAL, set.stateOf(GamePanelId.LOG))
        assertEquals(30, set.render(Canvas.of(ScreenBuffer(30, 10)), inputs, setOf(GamePanelId.LOG), 0).sides.single().width)
    }

    @Test
    fun `renders chrome even when content draws nothing`() {
        val buffer = renderPanel(panel(normal = { contentView { } }))

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("9", buffer.get(3, 0).char)
    }

    @Test
    fun `scroll offset persists and is only observed after rendering`() {
        val unscrolled = renderPanel(panel(normal = { stubContent(20) }))
        val panel = panel(normal = { stubContent(20) })
        val set = PanelSet.uniform(listOf(panel))

        val firstAfterScroll = run {
            set.scrollFocused(0, 3)
            render(set)
        }
        val secondAfterScroll = render(set)

        assertNotEquals(unscrolled.text(), firstAfterScroll.text())
        assertEquals(firstAfterScroll.text(), secondAfterScroll.text())
    }

    @Test
    fun `resize re-follows a settled reveal without caller reset plumbing`() {
        val revealing = contentView { cursor ->
            repeat(20) { row -> cursor.writeLine("row$row") }
            cursor.markRevealAt(15)
        }
        val panel = panel(normal = { revealing })
        val set = PanelSet.uniform(listOf(panel))

        render(set)
        set.scrollFocused(0, -100)
        val stayedAway = render(set)
        val forced = render(set, height = 8)

        assertNotEquals(stayedAway.text(), forced.text())
    }

    @Test
    fun `focus controls border color through the set`() {
        val panel = panel()

        val focused = renderPanel(panel)
        assertEquals(ChromeRole.PANEL_BORDER_FOCUSED, focused.get(0, 0).style.fg)

        val unfocusedPanel = panel()
        val unfocusedSet = PanelSet.uniform(listOf(unfocusedPanel, Panel(
            id = GamePanelId.BOARD,
            title = "B",
            normal = { Panel.Presentation.allocated(contentView { }) },
        )))
        unfocusedSet.focus(GamePanelId.BOARD)
        val buffer = ScreenBuffer(30, 10)
        unfocusedSet.render(Canvas.of(buffer), inputs, setOf(GamePanelId.LOG, GamePanelId.BOARD), 0)
        assertEquals(ChromeRole.PANEL_BORDER, buffer.get(0, 0).style.fg)
    }

    @Test
    fun `minimized presentation owns its width and never builds normal content`() {
        var normalBuilds = 0
        val panel = panel(
            normal = {
                normalBuilds++
                stubContent(3)
            },
            minimized = { VerticalTitleView("LOG") },
        )

        val buffer = renderPanel(panel, width = Panel.MINIMIZED_WIDTH, configure = { it.cycleFocusedState(-1) })

        assertEquals("9", buffer.get(3, 0).char)
        assertEquals("L", buffer.get(3, 2).char)
        assertEquals(0, normalBuilds)
    }

    @Test
    fun `a focused panel's scrollbar thumb uses the focus color`() {
        val buffer = renderPanel(panel(normal = { stubContent(20) }))

        val thumbRow = (1..8).first { buffer.get(29, it).char == "▐" }
        assertEquals(ChromeRole.PANEL_BORDER_FOCUSED, buffer.get(29, thumbRow).style.fg)
    }
}
