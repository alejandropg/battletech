package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.Impulse
import battletech.tactical.action.Initiative
import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.definition.FireWeaponActionDefinition
import battletech.tactical.action.movement.MoveActionDefinition
import battletech.tactical.model.HexCoordinates
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.game.phase.MovementPhase
import battletech.tui.game.phase.PhaseServices
import com.github.ajalt.mordant.input.KeyboardEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class MovementSelectingUnitPhaseTest {

    private val services = PhaseServices(
        actionQueryService = ActionQueryService(
            MoveActionDefinition(),
            listOf(FireWeaponActionDefinition()),
        ),
    )

    private fun aTurnState(
        movementOrder: List<Impulse> = listOf(
            Impulse(PlayerId.PLAYER_1, 1),
            Impulse(PlayerId.PLAYER_2, 1),
        ),
        currentImpulseIndex: Int = 0,
        movedUnitIds: Set<UnitId> = emptySet(),
        unitsMovedInCurrentImpulse: Int = 0,
    ) = TurnState(
        initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
            loser = PlayerId.PLAYER_1,
            winner = PlayerId.PLAYER_2,
        ),
        movementSequence = ImpulseSequence(movementOrder, currentImpulseIndex),
        movedUnitIds = movedUnitIds,
        unitsMovedInCurrentImpulse = unitsMovedInCurrentImpulse,
    )

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

            val result = MovementPhase.SelectingUnit.handle(arrowUp(), state, services)

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

            val result = MovementPhase.SelectingUnit.handle(enterKey(), state, services)

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

            val result = MovementPhase.SelectingUnit.handle(enterKey(), state, services)

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

            val result = MovementPhase.SelectingUnit.handle(enterKey(), state, services)

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

            val result = MovementPhase.SelectingUnit.handle(enterKey(), state, services)

            assertNotNull(result)
            assertEquals(state, result!!.app)
            assertNull(result.flash)
        }
    }

    @Nested
    inner class CycleUnitTest {
        @Test
        fun `cycles to next selectable unit`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_1, position = HexCoordinates(2, 2))
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 5, rows = 5))
            val turnState = aTurnState()
            val state = anAppState(
                cursor = HexCoordinates(0, 0),
                gameState = gameState,
                turnState = turnState,
            )

            val result = MovementPhase.SelectingUnit.handle(tabKey(), state, services)

            assertNotNull(result)
            assertEquals(HexCoordinates(2, 2), result!!.app.cursor)
        }

        @Test
        fun `cycles back to first unit when at last`() {
            val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_1, position = HexCoordinates(2, 2))
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 5, rows = 5))
            val turnState = aTurnState()
            val state = anAppState(
                cursor = HexCoordinates(2, 2),
                gameState = gameState,
                turnState = turnState,
            )

            val result = MovementPhase.SelectingUnit.handle(tabKey(), state, services)

            assertNotNull(result)
            assertEquals(HexCoordinates(0, 0), result!!.app.cursor)
        }
    }
}
