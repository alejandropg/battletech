package battletech.tui.game

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.session.Impulse
import battletech.tactical.session.TurnState
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.ForeignUnit
import battletech.tactical.unit.UnitId
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aTurnState
import battletech.tui.aUnit
import battletech.tui.game.phase.MovementPhase
import com.github.ajalt.mordant.input.KeyboardEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class MovementSelectingUnitPhaseTest {

    private fun anAppState(
        cursor: HexCoordinates = HexCoordinates(0, 0),
        gameState: battletech.tactical.model.GameState = aGameState(),
        turnState: TurnState = TurnState.NULL,
    ): AppState = AppState(gameState, turnState, MovementPhase.SelectingUnit, cursor)

    private fun enterKey(): KeyboardEvent = KeyboardEvent("Enter")
    private fun tabKey(): KeyboardEvent = KeyboardEvent("Tab")
    private fun arrowUp(): KeyboardEvent = KeyboardEvent("ArrowUp")

    @Nested
    inner class MoveCursorTest {
        @Test
        fun `arrow key moves cursor`() {
            val map = aGameMap(cols = 5, rows = 5)
            val state = anAppState(cursor = HexCoordinates(2, 2), gameState = aGameState(map = map))

            val result = MovementPhase.SelectingUnit.handle(arrowUp(), state)

            assertNotNull(result)
            assertEquals(HexCoordinates(2, 1), result!!.app.cursor)
            assertNull(result.flash)
        }
    }

    @Nested
    inner class TrySelectUnitTest {
        @Test
        fun `returns flash for wrong player unit`() {
            val p2Unit = aUnit(id = "u2", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(p2Unit))
            val turnState = aTurnState()
            val state = anAppState(
                cursor = HexCoordinates(0, 0),
                gameState = gameState,
                turnState = turnState,
            )

            val result = MovementPhase.SelectingUnit.handle(enterKey(), state)

            assertNotNull(result)
            assertEquals("Not your unit", result!!.flash?.text)
        }

        @Test
        fun `returns flash for already moved unit`() {
            val p1Unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(p1Unit))
            val turnState = aTurnState(movedUnitIds = setOf(UnitId("u1")))
            val state = anAppState(
                cursor = HexCoordinates(0, 0),
                gameState = gameState,
                turnState = turnState,
            )

            val result = MovementPhase.SelectingUnit.handle(enterKey(), state)

            assertNotNull(result)
            assertEquals("Already moved", result!!.flash?.text)
        }

        @Test
        fun `enters browsing phase for valid unit`() {
            val p1Unit = aUnit(
                id = "u1", owner = PlayerId.PLAYER_1,
                position = HexCoordinates(0, 0), walkingMP = 4, runningMP = 6,
            )
            val map = aGameMap(cols = 5, rows = 5)
            val gameState = aGameState(units = listOf(p1Unit), map = map)
            val turnState = aTurnState()
            val state = anAppState(
                cursor = HexCoordinates(0, 0),
                gameState = gameState,
                turnState = turnState,
            )

            val result = MovementPhase.SelectingUnit.handle(enterKey(), state)

            assertNotNull(result)
            assertInstanceOf(MovementPhase.Browsing::class.java, result!!.app.phase)
            assertNull(result.flash)
        }

        @Test
        fun `no unit at cursor returns unchanged state`() {
            val gameState = aGameState()
            val state = anAppState(
                cursor = HexCoordinates(0, 0),
                gameState = gameState,
                turnState = aTurnState(),
            )

            val result = MovementPhase.SelectingUnit.handle(enterKey(), state)

            assertNotNull(result)
            assertEquals(state, result!!.app)
            assertNull(result.flash)
        }
    }

    @Nested
    inner class CycleUnitTest {
        @Test
        fun `cycles to next selectable unit and enters Browsing`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0), walkingMP = 4)
            val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_1, position = HexCoordinates(2, 2), walkingMP = 4)
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 5, rows = 5))
            val state = anAppState(cursor = HexCoordinates(0, 0), gameState = gameState, turnState = aTurnState())

            val result = MovementPhase.SelectingUnit.handle(tabKey(), state)

            assertNotNull(result)
            assertEquals(HexCoordinates(2, 2), result!!.app.cursor)
            val browsing = result.app.phase as MovementPhase.Browsing
            assertEquals(u2.id, browsing.unitId)
        }

        @Test
        fun `cycles back to first unit when at last and enters Browsing`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0), walkingMP = 4)
            val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_1, position = HexCoordinates(2, 2), walkingMP = 4)
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 5, rows = 5))
            val state = anAppState(cursor = HexCoordinates(2, 2), gameState = gameState, turnState = aTurnState())

            val result = MovementPhase.SelectingUnit.handle(tabKey(), state)

            assertNotNull(result)
            assertEquals(HexCoordinates(0, 0), result!!.app.cursor)
            val browsing = result.app.phase as MovementPhase.Browsing
            assertEquals(u1.id, browsing.unitId)
        }

        @Test
        fun `Tab with cursor on empty hex auto-selects first selectable unit`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(2, 2), walkingMP = 4)
            val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_1, position = HexCoordinates(4, 4), walkingMP = 4)
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 6, rows = 6))
            val state = anAppState(cursor = HexCoordinates(0, 0), gameState = gameState, turnState = aTurnState())

            val result = MovementPhase.SelectingUnit.handle(tabKey(), state)

            assertNotNull(result)
            assertEquals(HexCoordinates(2, 2), result!!.app.cursor)
            val browsing = result.app.phase as MovementPhase.Browsing
            assertEquals(u1.id, browsing.unitId)
        }

        @Test
        fun `Tab with no selectable units is a no-op`() {
            val state = anAppState(cursor = HexCoordinates(0, 0), gameState = aGameState(), turnState = aTurnState())

            val result = MovementPhase.SelectingUnit.handle(tabKey(), state)

            assertNotNull(result)
            assertEquals(state, result!!.app)
        }
    }

    @Nested
    inner class UnitStatusTest {
        @Test
        fun `unitStatus is Owned when cursor is over the active player's own unit`() {
            val p1Unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val p2Unit = aUnit(id = "u2", owner = PlayerId.PLAYER_2, position = HexCoordinates(1, 1))
            val gameState = aGameState(units = listOf(p1Unit, p2Unit))
            val state = anAppState(cursor = HexCoordinates(0, 0), gameState = gameState, turnState = aTurnState())

            val subject = MovementPhase.SelectingUnit.unitStatus(state)

            assertInstanceOf(CombatUnit::class.java, subject)
            assertEquals(p1Unit.id, (subject as CombatUnit).id)
        }

        @Test
        fun `unitStatus is Foreign when cursor is over an enemy unit`() {
            val p1Unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val p2Unit = aUnit(id = "u2", owner = PlayerId.PLAYER_2, position = HexCoordinates(1, 1))
            val gameState = aGameState(units = listOf(p1Unit, p2Unit))
            val state = anAppState(cursor = HexCoordinates(1, 1), gameState = gameState, turnState = aTurnState())

            val subject = MovementPhase.SelectingUnit.unitStatus(state)

            assertInstanceOf(ForeignUnit::class.java, subject)
            assertEquals(p2Unit.id, (subject as ForeignUnit).id)
        }

        @Test
        fun `unitStatus is Foreign for an enemy unit once movement completes`() {
            // Movement complete -> session.activePlayer is null. AppState.viewer no longer goes
            // null here: hot-seat falls back to PLAYER_1 (AppState.viewer's `?: PlayerId.PLAYER_1`)
            // through this transient system-phase-like moment, so the projection is still concrete.
            // p2Unit is foreign under that viewer regardless. (The engine's own "unknown viewer
            // fails closed" contract — see battletech.tactical.query.projectFor's KDoc — is
            // exercised directly by BattleSessionTest/EventRedactionTest, not through this
            // TUI-level state.)
            val p1Unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val p2Unit = aUnit(id = "u2", owner = PlayerId.PLAYER_2, position = HexCoordinates(1, 1))
            val gameState = aGameState(units = listOf(p1Unit, p2Unit))
            val completedTurnState = aTurnState(
                movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 1)),
                currentImpulseIndex = 1,
            )
            val state = anAppState(cursor = HexCoordinates(1, 1), gameState = gameState, turnState = completedTurnState)

            val subject = MovementPhase.SelectingUnit.unitStatus(state)

            assertInstanceOf(ForeignUnit::class.java, subject)
            assertEquals(p2Unit.id, (subject as ForeignUnit).id)
        }

        @Test
        fun `unitStatus stays Own for the last acting player's unit once movement completes`() {
            // Same transient moment as above (activePlayer null), but the cursor sits on the unit
            // that just finished acting. AppState.viewer falls back to PLAYER_1 rather than going
            // null, so their own unit keeps rendering as Own instead of flickering to Foreign.
            val p1Unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val p2Unit = aUnit(id = "u2", owner = PlayerId.PLAYER_2, position = HexCoordinates(1, 1))
            val gameState = aGameState(units = listOf(p1Unit, p2Unit))
            val completedTurnState = aTurnState(
                movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 1)),
                currentImpulseIndex = 1,
            )
            val state = anAppState(cursor = HexCoordinates(0, 0), gameState = gameState, turnState = completedTurnState)

            val subject = MovementPhase.SelectingUnit.unitStatus(state)

            assertInstanceOf(CombatUnit::class.java, subject)
            assertEquals(p1Unit.id, (subject as CombatUnit).id)
        }
    }
}
