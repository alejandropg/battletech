package battletech.tui.game

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aTurnState
import battletech.tui.aUnit
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.MovementPhase
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Remote-play seat enforcement in idle selecting states
 * ([MovementPhase.SelectingUnit], [AttackPhase.SelectingAttacker]).
 *
 * With [AppState.localPlayer] set (host = PLAYER_1, joiner = PLAYER_2), this
 * client may act only while its own seat is the active player. Tab, Enter,
 * click, and 'c' must all be blocked with the "Waiting for opponent" flash
 * on the opponent's turn — previously only Enter/click were guarded (via
 * `selectOwnUnit`), letting Tab and 'c' drive the opponent's units. Cursor
 * movement and hot-seat play (`localPlayer == null`) are unaffected.
 */
internal class RemoteSeatGuardTest {

    private fun enterKey(): KeyboardEvent = KeyboardEvent("Enter")
    private fun tabKey(): KeyboardEvent = KeyboardEvent("Tab")
    private fun cKey(): KeyboardEvent = KeyboardEvent("c")
    private fun arrowUp(): KeyboardEvent = KeyboardEvent("ArrowUp")

    // Maps to hex (0, 0) at BOARD_ORIGIN_X/Y = 2, 2 — see InputMapperTest's
    // `left click maps to hex coordinates`.
    private fun clickOnOrigin(): MouseEvent = MouseEvent(x = 5, y = 3, left = true)

    @Nested
    inner class MovementIdleBlockedOnOpponentTurn {
        private val p1Unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
        private val gameState = aGameState(units = listOf(p1Unit), map = aGameMap(cols = 5, rows = 5))
        private val turnState = aTurnState() // movement active player defaults to PLAYER_1

        private fun localP2State(cursor: HexCoordinates = HexCoordinates(0, 0)): AppState =
            AppState(gameState, turnState, MovementPhase.SelectingUnit, cursor)
                .copy(localPlayer = PlayerId.PLAYER_2)

        @Test
        fun `Tab is blocked with waiting flash and unchanged state`() {
            val original = localP2State()

            val result = MovementPhase.SelectingUnit.handle(tabKey(), original)

            assertNotNull(result)
            assertEquals("Waiting for opponent", result!!.flash?.text)
            assertEquals(original, result.app)
        }

        @Test
        fun `Enter on the active player's unit is blocked with waiting flash and unchanged state`() {
            val original = localP2State(cursor = HexCoordinates(0, 0))

            val result = MovementPhase.SelectingUnit.handle(enterKey(), original)

            assertNotNull(result)
            assertEquals("Waiting for opponent", result!!.flash?.text)
            assertEquals(original, result.app)
        }

        @Test
        fun `mouse click is blocked with waiting flash and unchanged state`() {
            val original = localP2State(cursor = HexCoordinates(3, 3))

            val result = MovementPhase.SelectingUnit.handle(clickOnOrigin(), original)

            assertNotNull(result)
            assertEquals("Waiting for opponent", result!!.flash?.text)
            assertEquals(original, result.app)
        }

        @Test
        fun `arrow key still moves the cursor`() {
            val original = localP2State(cursor = HexCoordinates(2, 2))

            val result = MovementPhase.SelectingUnit.handle(arrowUp(), original)

            assertNotNull(result)
            assertEquals(HexCoordinates(2, 1), result!!.app.cursor)
            assertEquals(null, result.flash)
        }
    }

    @Nested
    inner class AttackIdleBlockedOnOpponentTurn {
        private val gameState = aGameState()
        private val turnState = aTurnState() // attack active player defaults to PLAYER_1

        private fun localP2State(): AppState =
            AppState(gameState, turnState, AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK), HexCoordinates(0, 0))
                .copy(localPlayer = PlayerId.PLAYER_2)

        @Test
        fun `commit is blocked with waiting flash and unchanged state`() {
            val original = localP2State()

            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK).handle(cKey(), original)

            assertNotNull(result)
            assertEquals("Waiting for opponent", result!!.flash?.text)
            assertEquals(original, result.app)
        }
    }

    @Nested
    inner class HotSeatRegression {
        @Test
        fun `Tab still cycles and enters the sub-mode when localPlayer is null`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0), walkingMP = 4)
            val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_1, position = HexCoordinates(2, 2), walkingMP = 4)
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 5, rows = 5))
            val state = AppState(gameState, aTurnState(), MovementPhase.SelectingUnit, HexCoordinates(0, 0))
                .copy(localPlayer = null)

            val result = MovementPhase.SelectingUnit.handle(tabKey(), state)

            assertNotNull(result)
            assertEquals(HexCoordinates(2, 2), result!!.app.cursor)
            val browsing = assertInstanceOf(MovementPhase.Browsing::class.java, result.app.phase)
            assertEquals(u2.id, browsing.unitId)
        }
    }
}
