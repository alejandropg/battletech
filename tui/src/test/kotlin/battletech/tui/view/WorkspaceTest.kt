package battletech.tui.view

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MatchOutcome
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.session.MatchEnded
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.anAppState
import battletech.tui.game.GamePanelId
import battletech.tui.game.phase.MovementPhase
import battletech.tui.input.Keybindings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.screen.ChromeRole
import tenter.screen.ScreenBuffer
import tenter.view.FlashMessage
import tenter.view.HelpView
import tenter.view.ScrollOffset
import tenter.view.text

/**
 * [Workspace] composes the board, every visible side panel, the status bar, and the game-over
 * banner into one frame, and answers hit-tests against the layout it last rendered. It replaces
 * `FrameLayout` + `PlacedPanel` + `composeFrame`/`Frame` — see its KDoc for why panel state no
 * longer round-trips through `AppState` the way it used to.
 *
 * Panel-level persistence (state, scroll surviving across renders on their own) is [PanelTest]'s
 * job; these tests cover the composition: layout, visibility, focus, hit-testing, and the board.
 */
internal class WorkspaceTest {

    private val appState = anAppState(MovementPhase.SelectingUnit, gameState = aGameState(map = aGameMap()))

    @Test
    fun `visible panels render, in Panels_build order, right-aligned against the board`() {
        val workspace = Workspace(Keybindings.DEFAULT)

        val buffer = workspace.render(appState, width = 120, height = 40, flash = null)

        assertTrue(buffer.text().contains(UnitStatusView.TITLE))
        assertTrue(buffer.text().contains(LogView.TITLE))
        // LOG is last among MOVEMENT's visible panels in Panels.build()'s order, so it sits
        // flush against the right edge.
        assertEquals(GamePanelId.LOG, workspace.panelAt(118, 10))
    }

    @Test
    fun `HELP does not exist in the layout until AppState_helpOpen is set`() {
        val workspace = Workspace(Keybindings.DEFAULT)

        val closed = workspace.render(appState, width = 120, height = 40, flash = null)
        assertFalse(closed.text().contains(HelpView.TITLE))

        val opened = workspace.render(appState.copy(helpOpen = true), width = 120, height = 40, flash = null)
        assertTrue(opened.text().contains(HelpView.TITLE))
    }

    @Test
    fun `selected unit prefixes action prompt but not temporary flash`() {
        val unit = aUnit(id = "W1", name = "Wolverine WVR-6R")
        val phase = MovementPhase.Browsing(
            unitId = unit.id,
            modes = listOf(ReachabilityMap(MovementMode.WALK, maxMP = 5, destinations = emptyList())),
            currentModeIndex = 0,
            hoveredDestination = null,
        )
        val selected = anAppState(phase, gameState = aGameState(units = listOf(unit), map = aGameMap()))
        val workspace = Workspace(Keybindings.DEFAULT)

        val promptBuffer = workspace.render(selected, width = 120, height = 40, flash = null)
        val flashBuffer = workspace.render(selected, width = 120, height = 40, flash = FlashMessage("Not available"))

        assertTrue(statusRow(promptBuffer).contains("W1: Wolverine WVR-6R ┆ Walk (5 MP)"))
        assertFalse(statusRow(flashBuffer).contains("W1: Wolverine WVR-6R"))
        assertTrue(statusRow(flashBuffer).contains("Not available"))
    }

    @Test
    fun `minimizing the focused panel shrinks it to its stub — no more horizontal title, board absorbs the freed width`() {
        val workspace = Workspace(Keybindings.DEFAULT)
        workspace.render(appState, width = 120, height = 40, flash = null)

        workspace.focus(GamePanelId.LOG)
        workspace.cycleFocusedState(-1) // NORMAL -> MINIMIZED
        val buffer = workspace.render(appState, width = 120, height = 40, flash = null)

        assertFalse(buffer.text().contains(LogView.TITLE), "minimized panel draws its title one letter per row")
        // LOG's stub is 7 wide, so it now spans columns 113..119 — and unlike the collapsed panels
        // this replaced, a stub IS a hit-test target: it has nothing to scroll, but swallowing the
        // click keeps it from falling through to the board's click-to-hex mapping.
        assertEquals(GamePanelId.LOG, workspace.panelAt(118, 10), "a minimized stub is still a scroll target")
    }

    @Test
    fun `panelAt returns null over the board and the status bar`() {
        val workspace = Workspace(Keybindings.DEFAULT)
        workspace.render(appState, width = 120, height = 40, flash = null)

        assertNull(workspace.panelAt(x = 5, y = 10), "board area")
        assertNull(workspace.panelAt(x = 118, y = 1), "status bar row")
    }

    @Test
    fun `scrollPanel does not touch the board`() {
        val workspace = Workspace(Keybindings.DEFAULT)
        workspace.render(appState, width = 120, height = 40, flash = null)

        workspace.scrollPanel(GamePanelId.UNIT_STATUS, delta = 3)
        workspace.render(appState, width = 120, height = 40, flash = null)

        assertEquals(ScrollOffset.ZERO, workspace.boardOffset, "scrolling a panel must not touch the board")
    }

    @Test
    fun `scrollPanel on a panel that is not currently placed is a safe no-op`() {
        val workspace = Workspace(Keybindings.DEFAULT)
        workspace.render(appState, width = 120, height = 40, flash = null) // TARGETS isn't visible in MOVEMENT

        workspace.scrollPanel(GamePanelId.TARGETS, delta = 3) // must not throw
        val buffer = workspace.render(appState, width = 120, height = 40, flash = null)

        assertFalse(buffer.text().contains(TargetsView.TITLE))
    }

    @Test
    fun `boardOffset reflects a manual pan once the cursor isn't driving auto-follow`() {
        val wideMap = anAppState(MovementPhase.SelectingUnit, gameState = aGameState(map = aGameMap(cols = 60, rows = 20)))
        val workspace = Workspace(Keybindings.DEFAULT)

        // First render follows the cursor into view. The cursor doesn't move afterward, so a
        // manual pan on top of that settled offset must survive the next render untouched.
        workspace.render(wideMap, width = 80, height = 30, flash = null)
        workspace.panBoard(5, 0)
        workspace.render(wideMap, width = 80, height = 30, flash = null)

        assertEquals(5, workspace.boardOffset.x)
    }

    @Test
    fun `the focused panel renders a green border while unfocused panels render neutral`() {
        val workspace = Workspace(Keybindings.DEFAULT)
        workspace.focus(GamePanelId.LOG)

        val buffer = workspace.render(appState, width = 120, height = 40, flash = null)

        // MOVEMENT's visible panels are UNIT_STATUS then LOG (Panels.build order), each 28 wide,
        // right-aligned against a 120-wide screen: UNIT_STATUS spans 64..91, LOG spans 92..119.
        assertEquals(ChromeRole.PANEL_BORDER_FOCUSED, buffer.get(92, 4).style.fg, "LOG (focused) border corner")
        assertEquals(ChromeRole.PANEL_BORDER, buffer.get(64, 4).style.fg, "UNIT_STATUS (unfocused) border corner")
    }

    @Test
    fun `a maximized panel hides the board and the other panels`() {
        val workspace = Workspace(Keybindings.DEFAULT)
        workspace.focus(GamePanelId.LOG)
        workspace.cycleFocusedState(1) // NORMAL -> MAXIMIZED

        val buffer = workspace.render(appState, width = 120, height = 40, flash = null)

        assertTrue(buffer.text().contains(LogView.TITLE))
        assertFalse(buffer.text().contains(UnitStatusView.TITLE), "UNIT_STATUS must not be laid out while LOG is maximized")
        assertFalse(buffer.text().contains("TACTICAL MAP"), "the board must not be laid out while a side panel is maximized")
    }

    @Test
    fun `maximizing UNIT_STATUS shows the record sheet, not the compact list`() {
        val unit = aUnit(id = "A1", position = HexCoordinates(0, 0))
        val state = anAppState(MovementPhase.SelectingUnit, gameState = aGameState(units = listOf(unit), map = aGameMap()))
        val workspace = Workspace(Keybindings.DEFAULT)

        val normal = workspace.render(state, width = 120, height = 40, flash = null)
        assertTrue(normal.text().contains(UnitStatusView.TITLE))
        assertFalse(normal.text().contains("'MECH DATA"), "NORMAL still renders the compact UnitStatusView")

        workspace.focus(GamePanelId.UNIT_STATUS)
        workspace.cycleFocusedState(1) // NORMAL -> MAXIMIZED
        val maximized = workspace.render(state, width = 200, height = 200, flash = null)

        assertTrue(maximized.text().contains(UnitStatusView.TITLE), "same panel, same title")
        assertTrue(maximized.text().contains("'MECH DATA"), "MAXIMIZED renders the record sheet")
    }

    @Test
    fun `game-over banner renders once matchEnded is set`() {
        val workspace = Workspace(Keybindings.DEFAULT)
        val ended = appState.copy(matchEnded = MatchEnded(MatchOutcome.Victory(PlayerId.PLAYER_1)))

        val buffer = workspace.render(ended, width = 120, height = 40, flash = null)

        assertTrue(buffer.text().contains("MATCH OVER"))
        assertTrue(buffer.text().contains("P1 wins!"))
    }

    private fun statusRow(buffer: ScreenBuffer): String =
        (0 until buffer.width).joinToString("") { buffer.get(it, 1).char }
}
