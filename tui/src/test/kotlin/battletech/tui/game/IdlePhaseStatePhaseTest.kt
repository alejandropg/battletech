package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.InitiativeResult
import battletech.tactical.action.MovementImpulse
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.definition.FireWeaponActionDefinition
import battletech.tactical.action.movement.MoveActionDefinition
import battletech.tactical.model.HexCoordinates
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aUnit
import com.github.ajalt.mordant.input.KeyboardEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class IdlePhaseStatePhaseTest {

    private val actionQueryService = ActionQueryService(
        MoveActionDefinition(),
        listOf(FireWeaponActionDefinition()),
    )
    private val manager = PhaseManager(
        movementController = MovementController(actionQueryService),
        attackController = AttackController(),
    )

    private fun aTurnState(
        movementOrder: List<MovementImpulse> = listOf(
            MovementImpulse(PlayerId.PLAYER_1, 1),
            MovementImpulse(PlayerId.PLAYER_2, 1),
        ),
        currentImpulseIndex: Int = 0,
        movedUnitIds: Set<UnitId> = emptySet(),
        unitsMovedInCurrentImpulse: Int = 0,
        attackOrder: List<MovementImpulse> = emptyList(),
        attackedUnitIds: Set<UnitId> = emptySet(),
        currentAttackImpulseIndex: Int = 0,
        unitsAttackedInCurrentImpulse: Int = 0,
    ) = TurnState(
        initiativeResult = InitiativeResult(
            rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
            loser = PlayerId.PLAYER_1,
            winner = PlayerId.PLAYER_2,
        ),
        movementOrder = movementOrder,
        currentImpulseIndex = currentImpulseIndex,
        movedUnitIds = movedUnitIds,
        unitsMovedInCurrentImpulse = unitsMovedInCurrentImpulse,
        attackOrder = attackOrder,
        attackedUnitIds = attackedUnitIds,
        currentAttackImpulseIndex = currentAttackImpulseIndex,
        unitsAttackedInCurrentImpulse = unitsAttackedInCurrentImpulse,
    )

    private fun anAppState(
        currentPhase: TurnPhase = TurnPhase.MOVEMENT,
        cursor: HexCoordinates = HexCoordinates(0, 0),
        gameState: battletech.tactical.model.GameState = aGameState(),
        turnState: TurnState? = null,
    ): AppState {
        return AppState(
            gameState = gameState,
            currentPhase = currentPhase,
            cursor = cursor,
            phase = IdlePhaseState(),
            turnState = turnState,
        )
    }

    private fun idlePhase(prompt: String = "Move cursor to select a unit"): IdlePhaseState =
        IdlePhaseState(prompt)

    private fun enterKey(): KeyboardEvent = KeyboardEvent("Enter")
    private fun tabKey(): KeyboardEvent = KeyboardEvent("Tab")
    private fun cKey(): KeyboardEvent = KeyboardEvent("c")
    private fun arrowUp(): KeyboardEvent = KeyboardEvent("ArrowUp")

    @Nested
    inner class MoveCursorTest {
        @Test
        fun `arrow key moves cursor`() {
            val map = aGameMap(cols = 5, rows = 5)
            val state = anAppState(cursor = HexCoordinates(2, 2), gameState = aGameState(map = map))
            val phase = idlePhase()

            val result = phase.processEvent(arrowUp(), state, manager)

            assertNotNull(result)
            assertEquals(HexCoordinates(2, 1), result!!.appState.cursor)
            assertNull(result.flash)
        }
    }

    @Nested
    inner class ClickHexTest {
        @Test
        fun `click hex updates cursor and tries selection`() {
            val p1Unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(1, 1), walkingMP = 4, runningMP = 6)
            val map = aGameMap(cols = 5, rows = 5)
            val gameState = aGameState(units = listOf(p1Unit), map = map)
            val turnState = aTurnState()
            val state = anAppState(
                cursor = HexCoordinates(0, 0),
                gameState = gameState,
                turnState = turnState,
            )
            val phase = idlePhase()

            // Simulate a click on hex (1,1) where unit is — use Enter on cursor at unit position
            val stateAtUnit = state.copy(cursor = HexCoordinates(1, 1))
            val result = phase.processEvent(enterKey(), stateAtUnit, manager)

            assertNotNull(result)
            // Should enter movement phase (Browsing)
            assertInstanceOf(MovementPhaseState.Browsing::class.java, result!!.appState.phase)
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
            val phase = idlePhase()

            val result = phase.processEvent(enterKey(), state, manager)

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
            val phase = idlePhase()

            val result = phase.processEvent(enterKey(), state, manager)

            assertNotNull(result)
            assertEquals("Already moved", result!!.flash?.text)
        }

        @Test
        fun `enters movement phase for valid unit`() {
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
            val phase = idlePhase()

            val result = phase.processEvent(enterKey(), state, manager)

            assertNotNull(result)
            assertInstanceOf(MovementPhaseState.Browsing::class.java, result!!.appState.phase)
            assertNull(result.flash)
        }

        @Test
        fun `enters attack phase for valid unit during weapon attack`() {
            val p1Unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(p1Unit))
            val turnState = aTurnState(
                attackOrder = listOf(
                    MovementImpulse(PlayerId.PLAYER_1, 1),
                    MovementImpulse(PlayerId.PLAYER_2, 1),
                ),
            )
            val state = anAppState(
                currentPhase = TurnPhase.WEAPON_ATTACK,
                cursor = HexCoordinates(0, 0),
                gameState = gameState,
                turnState = turnState,
            )
            val phase = idlePhase()

            val result = phase.processEvent(enterKey(), state, manager)

            assertNotNull(result)
            assertInstanceOf(AttackPhaseState::class.java, result!!.appState.phase)
        }

        @Test
        fun `returns flash for already committed unit during attack`() {
            val p1Unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(p1Unit))
            val turnState = aTurnState(
                attackOrder = listOf(
                    MovementImpulse(PlayerId.PLAYER_1, 1),
                ),
                attackedUnitIds = setOf(UnitId("u1")),
            )
            val state = anAppState(
                currentPhase = TurnPhase.WEAPON_ATTACK,
                cursor = HexCoordinates(0, 0),
                gameState = gameState,
                turnState = turnState,
            )
            val phase = idlePhase()

            val result = phase.processEvent(enterKey(), state, manager)

            assertNotNull(result)
            assertEquals("Already committed attacks", result!!.flash?.text)
        }

        @Test
        fun `no unit at cursor returns unchanged state`() {
            val gameState = aGameState()
            val state = anAppState(
                cursor = HexCoordinates(0, 0),
                gameState = gameState,
                turnState = aTurnState(),
            )
            val phase = idlePhase()

            val result = phase.processEvent(enterKey(), state, manager)

            assertNotNull(result)
            assertEquals(state, result!!.appState)
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
            val phase = idlePhase()

            val result = phase.processEvent(tabKey(), state, manager)

            assertNotNull(result)
            assertEquals(HexCoordinates(2, 2), result!!.appState.cursor)
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
            val phase = idlePhase()

            val result = phase.processEvent(tabKey(), state, manager)

            assertNotNull(result)
            assertEquals(HexCoordinates(0, 0), result!!.appState.cursor)
        }
    }

    @Nested
    inner class CommitDeclarationsTest {
        @Test
        fun `commit when not in attack phase does nothing`() {
            val state = anAppState(currentPhase = TurnPhase.MOVEMENT, turnState = aTurnState())
            val phase = idlePhase()

            val result = phase.processEvent(cKey(), state, manager)

            assertNotNull(result)
            assertEquals(state, result!!.appState)
            assertNull(result.flash)
        }

        @Test
        fun `commit returns flash when not all declared`() {
            val turnState = aTurnState(
                attackOrder = listOf(MovementImpulse(PlayerId.PLAYER_1, 1)),
            )
            manager.attackController.initializeImpulse(PlayerId.PLAYER_1, 1)
            val state = anAppState(
                currentPhase = TurnPhase.WEAPON_ATTACK,
                turnState = turnState,
            )
            val phase = idlePhase()

            val result = phase.processEvent(cKey(), state, manager)

            assertNotNull(result)
            assertNotNull(result!!.flash)
            assert(result.flash!!.text.contains("Declare all units first"))
        }
    }
}
