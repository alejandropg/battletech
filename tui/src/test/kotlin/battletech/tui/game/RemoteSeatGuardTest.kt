package battletech.tui.game

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.Impulse
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
 * A client may act only for a seat present in [AppState.seats]. Host/join play puts exactly one
 * seat there (e.g. PLAYER_2 for a joiner), so this client may act only while that seat is the
 * active player. Tab, Enter, click, and 'c' must all be blocked with the "Waiting for opponent"
 * flash on the opponent's turn — previously only Enter/click were guarded (via `selectOwnUnit`),
 * letting Tab and 'c' drive the opponent's units. Cursor movement is unaffected, and hot-seat
 * (both seats present) bypasses the gate entirely — not via any conditional, but because
 * [AppState.seats] holds both players there, so the active player is always a member. See
 * [HotSeatRegression] for the regression guard on that specifically.
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
            AppState(gameState, turnState, MovementPhase.SelectingUnit, cursor).let {
                it.copy(seats = mapOf(PlayerId.PLAYER_2 to it.anySession))
            }

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
            AppState(gameState, turnState, AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK), HexCoordinates(0, 0)).let {
                it.copy(seats = mapOf(PlayerId.PLAYER_2 to it.anySession))
            }

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
        fun `Tab still cycles and enters the sub-mode in hot-seat`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0), walkingMP = 4)
            val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_1, position = HexCoordinates(2, 2), walkingMP = 4)
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 5, rows = 5))
            // The default AppState factory maps both seats to the same session — hot-seat.
            val state = AppState(gameState, aTurnState(), MovementPhase.SelectingUnit, HexCoordinates(0, 0))

            val result = MovementPhase.SelectingUnit.handle(tabKey(), state)

            assertNotNull(result)
            assertEquals(HexCoordinates(2, 2), result!!.app.cursor)
            val browsing = assertInstanceOf(MovementPhase.Browsing::class.java, result.app.phase)
            assertEquals(u2.id, browsing.unitId)
        }

        /**
         * The regression guard for the whole "seats carry the information, not a flag" premise:
         * with both seats present (hot-seat), PLAYER_2's turn must be actionable by this process
         * too — proving hot-seat bypasses the seat check because [AppState.seats] contains both
         * players, not because of a `seats.size == 2` (or similar) conditional anywhere in the
         * guard itself.
         */
        @Test
        fun `PLAYER_2's turn accepts input for PLAYER_2 in hot-seat`() {
            val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, 0), walkingMP = 4)
            val gameState = aGameState(units = listOf(u2), map = aGameMap(cols = 5, rows = 5))
            val turnState = aTurnState(movementOrder = listOf(Impulse(PlayerId.PLAYER_2, 1)))
            val state = AppState(gameState, turnState, MovementPhase.SelectingUnit, HexCoordinates(0, 0))

            val result = MovementPhase.SelectingUnit.handle(enterKey(), state)

            assertNotNull(result)
            assertEquals(null, result!!.flash)
            assertInstanceOf(MovementPhase.Browsing::class.java, result.app.phase)
        }
    }
}
