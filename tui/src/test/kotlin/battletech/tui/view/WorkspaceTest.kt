package battletech.tui.view

import battletech.tactical.model.MatchOutcome
import battletech.tactical.model.PlayerId
import battletech.tactical.session.MatchEnded
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.anAppState
import battletech.tui.game.PanelId
import battletech.tui.game.phase.MovementPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.view.HelpView
import tenter.view.ScrollOffset
import tenter.view.text

/**
 * [Workspace] composes the board, every visible side panel, the status bar, and the game-over
 * banner into one frame, and answers hit-tests against the layout it last rendered. It replaces
 * `FrameLayout` + `PlacedPanel` + `composeFrame`/`Frame` — see its KDoc for why panel state no
 * longer round-trips through `AppState` the way it used to.
 *
 * Panel-level persistence (collapse, scroll surviving across renders on their own) is [PanelTest]'s
 * job; these tests cover the composition: layout, visibility, hit-testing, and the board.
 */
internal class WorkspaceTest {

    private val appState = anAppState(MovementPhase.SelectingUnit, gameState = aGameState(map = aGameMap(cols = 3, rows = 3)))

    @Test
    fun `visible panels render, in Panels_build order, right-aligned against the board`() {
        val workspace = Workspace()

        val buffer = workspace.render(appState, width = 120, height = 40, flash = null)

        assertTrue(buffer.text().contains(UnitStatusView.TITLE))
        assertTrue(buffer.text().contains(LogView.TITLE))
        // LOG is last among MOVEMENT's visible panels in Panels.build()'s order, so it sits
        // flush against the right edge.
        assertEquals(PanelId.LOG, workspace.panelAt(118, 10))
    }

    @Test
    fun `HELP does not exist in the layout until AppState_helpOpen is set`() {
        val workspace = Workspace()

        val closed = workspace.render(appState, width = 120, height = 40, flash = null)
        assertFalse(closed.text().contains(HelpView.TITLE))

        val opened = workspace.render(appState.copy(helpOpen = true), width = 120, height = 40, flash = null)
        assertTrue(opened.text().contains(HelpView.TITLE))
    }

    @Test
    fun `toggleCollapsed shrinks a panel to its stub — no more horizontal title, board absorbs the freed width`() {
        val workspace = Workspace()
        workspace.render(appState, width = 120, height = 40, flash = null)

        workspace.toggleCollapsed(PanelId.LOG)
        val buffer = workspace.render(appState, width = 120, height = 40, flash = null)

        assertFalse(buffer.text().contains(LogView.TITLE), "collapsed panel draws its title one letter per row")
        assertNull(workspace.panelAt(118, 10), "a collapsed panel is not a scroll target")
    }

    @Test
    fun `panelAt returns null over the board and the status bar`() {
        val workspace = Workspace()
        workspace.render(appState, width = 120, height = 40, flash = null)

        assertNull(workspace.panelAt(x = 5, y = 10), "board area")
        assertNull(workspace.panelAt(x = 118, y = 1), "status bar row")
    }

    @Test
    fun `scrollPanel does not touch the board`() {
        val workspace = Workspace()
        workspace.render(appState, width = 120, height = 40, flash = null)

        workspace.scrollPanel(PanelId.UNIT_STATUS, delta = 3)
        workspace.render(appState, width = 120, height = 40, flash = null)

        assertEquals(ScrollOffset.ZERO, workspace.boardOffset, "scrolling a panel must not touch the board")
    }

    @Test
    fun `scrollPanel on a panel that is not currently placed is a safe no-op`() {
        val workspace = Workspace()
        workspace.render(appState, width = 120, height = 40, flash = null) // TARGETS isn't visible in MOVEMENT

        workspace.scrollPanel(PanelId.TARGETS, delta = 3) // must not throw
        val buffer = workspace.render(appState, width = 120, height = 40, flash = null)

        assertFalse(buffer.text().contains(TargetsView.TITLE))
    }

    @Test
    fun `boardOffset reflects the given AppState_boardScroll once the cursor isn't driving auto-follow`() {
        val wideMap = anAppState(MovementPhase.SelectingUnit, gameState = aGameState(map = aGameMap(cols = 60, rows = 20)))
        val workspace = Workspace()

        // First render follows the cursor into view — same as any first render. The cursor doesn't
        // move on the second render, so its focus doesn't either, and the given offset sticks.
        workspace.render(wideMap, width = 80, height = 30, flash = null)
        workspace.render(wideMap.copy(boardScroll = ScrollOffset(5, 0)), width = 80, height = 30, flash = null)

        assertEquals(5, workspace.boardOffset.x)
    }

    @Test
    fun `game-over banner renders once matchEnded is set`() {
        val workspace = Workspace()
        val ended = appState.copy(matchEnded = MatchEnded(MatchOutcome.Victory(PlayerId.PLAYER_1)))

        val buffer = workspace.render(ended, width = 120, height = 40, flash = null)

        assertTrue(buffer.text().contains("MATCH OVER"))
        assertTrue(buffer.text().contains("P1 wins!"))
    }
}
