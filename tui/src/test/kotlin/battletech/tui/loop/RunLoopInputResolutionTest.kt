package battletech.tui.loop

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MatchOutcome
import battletech.tactical.model.PlayerId
import battletech.tactical.session.MatchEnded
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aTurnState
import battletech.tui.aUnit
import battletech.tui.game.AppState
import battletech.tui.game.GamePanelId
import battletech.tui.game.phase.BOARD_ORIGIN_X
import battletech.tui.game.phase.BOARD_ORIGIN_Y
import battletech.tui.game.phase.MovementPhase
import battletech.tui.input.BoardClick
import battletech.tui.input.ChromeAction
import battletech.tui.input.IdleAction
import battletech.tui.input.Keybindings
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tenter.input.ScrollAction

/**
 * [resolveInput] is where "what input is live this frame" is decided, and the only place the
 * match-ended block can be checked at all: once the match is over `Workspace.render` swaps the
 * status bar for the match-over line and stops drawing flash text, so a blocked input and a
 * handled one render identically. A loop-level test asserting the absence of a flash after
 * `MatchEnded` therefore passes whether or not the block exists — which is exactly what the two
 * tests this one replaces were doing.
 */
internal class RunLoopInputResolutionTest {

    private val keys = Keybindings.DEFAULT

    private fun appState(): AppState = AppState(
        gameState = aGameState(
            units = listOf(aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, 0))),
            map = aGameMap(cols = 5, rows = 5),
        ),
        turnState = aTurnState(),
        phase = MovementPhase.SelectingUnit,
        cursor = HexCoordinates(0, 0),
    )

    private fun ended(): AppState =
        appState().copy(matchEnded = MatchEnded(MatchOutcome.Victory(PlayerId.PLAYER_1)))

    /** A left click on the board's hex (0,0) at zero scroll. */
    private val boardClickEvent = MouseEvent(x = BOARD_ORIGIN_X, y = BOARD_ORIGIN_Y, left = true)

    @Test
    fun `a phase chord resolves while the match is live`() {
        assertEquals(
            IdleAction.SelectUnit,
            resolveInput(KeyboardEvent("Enter"), keys, GamePanelId.BOARD, appState()),
        )
    }

    @Test
    fun `a phase chord resolves to nothing once the match has ended`() {
        assertNull(resolveInput(KeyboardEvent("Enter"), keys, GamePanelId.BOARD, ended()))
    }

    @Test
    fun `a board click resolves while the match is live`() {
        assertEquals(
            BoardClick(HexCoordinates(0, 0)),
            resolveInput(boardClickEvent, keys, GamePanelId.BOARD, appState()),
        )
    }

    /**
     * The half that does NOT fall out of [activeContexts]: a click never goes through the keymap,
     * so dropping the phase's key layer blocks the keyboard and leaves the mouse driving the game.
     */
    @Test
    fun `a board click resolves to nothing once the match has ended`() {
        assertNull(resolveInput(boardClickEvent, keys, GamePanelId.BOARD, ended()))
    }

    @Test
    fun `chrome chords stay live after the match has ended`() {
        assertEquals(
            ChromeAction.ToggleHelp,
            resolveInput(KeyboardEvent("h", alt = true), keys, GamePanelId.BOARD, ended()),
        )
        assertEquals(
            ChromeAction.FocusPanel(GamePanelId.LOG),
            resolveInput(KeyboardEvent("9", alt = true), keys, GamePanelId.BOARD, ended()),
        )
    }

    @Test
    fun `panel-scroll chords are live only while a side panel is focused`() {
        assertEquals(
            ScrollAction.Lines(1),
            resolveInput(KeyboardEvent("ArrowDown"), keys, GamePanelId.LOG, appState()),
        )
        // On the board the same chord falls through to the phase, so wasd/arrows still move the cursor.
        assertEquals(
            IdleAction.MoveCursor(HexDirection.S),
            resolveInput(KeyboardEvent("ArrowDown"), keys, GamePanelId.BOARD, appState()),
        )
    }
}
