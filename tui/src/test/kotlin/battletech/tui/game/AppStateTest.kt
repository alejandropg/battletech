package battletech.tui.game

import battletech.tactical.action.InitiativeResult
import battletech.tactical.action.MovementImpulse
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class AppStateTest {

    private fun aTurnState(
        movementOrder: List<MovementImpulse> = listOf(
            MovementImpulse(PlayerId.PLAYER_1, 1),
            MovementImpulse(PlayerId.PLAYER_2, 1),
        ),
        currentImpulseIndex: Int = 0,
        movedUnitIds: Set<UnitId> = emptySet(),
        unitsMovedInCurrentImpulse: Int = 0,
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
    )

    private fun anAppState(
        currentPhase: TurnPhase = TurnPhase.MOVEMENT,
        phaseState: PhaseState = PhaseState.Idle(),
        cursor: HexCoordinates = HexCoordinates(0, 0),
        gameState: battletech.tactical.model.GameState = aGameState(),
        turnState: TurnState? = null,
    ) = AppState(
        gameState = gameState,
        currentPhase = currentPhase,
        cursor = cursor,
        phaseState = phaseState,
        turnState = turnState,
    )

    @Nested
    inner class NextPhaseTest {
        @Test
        fun `movement advances to weapon attack`() {
            assertEquals(TurnPhase.WEAPON_ATTACK, nextPhase(TurnPhase.MOVEMENT))
        }

        @Test
        fun `weapon attack advances to physical attack`() {
            assertEquals(TurnPhase.PHYSICAL_ATTACK, nextPhase(TurnPhase.WEAPON_ATTACK))
        }

        @Test
        fun `end advances to initiative`() {
            assertEquals(TurnPhase.INITIATIVE, nextPhase(TurnPhase.END))
        }

        @Test
        fun `full phase cycle`() {
            val phases = listOf(
                TurnPhase.INITIATIVE,
                TurnPhase.MOVEMENT,
                TurnPhase.WEAPON_ATTACK,
                TurnPhase.PHYSICAL_ATTACK,
                TurnPhase.HEAT,
                TurnPhase.END,
            )
            var current = TurnPhase.INITIATIVE
            val visited = mutableListOf(current)
            repeat(6) {
                current = nextPhase(current)
                visited.add(current)
            }
            assertEquals(phases + TurnPhase.INITIATIVE, visited)
        }
    }

    @Nested
    inner class HandlePhaseOutcomeTest {
        @Test
        fun `Continue updates phase state`() {
            val newPhase = PhaseState.Idle("new prompt")
            val state = anAppState()

            val result = handlePhaseOutcome(PhaseOutcome.Continue(newPhase), state)

            assertEquals(newPhase, result.phaseState)
            assertEquals(state.gameState, result.gameState)
            assertEquals(state.currentPhase, result.currentPhase)
        }

        @Test
        fun `Complete during movement with turnState advances impulse`() {
            val unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(unit))
            val newGameState = aGameState(units = listOf(unit.copy(position = HexCoordinates(1, 1))))
            val turnState = aTurnState(
                movementOrder = listOf(
                    MovementImpulse(PlayerId.PLAYER_1, 1),
                    MovementImpulse(PlayerId.PLAYER_2, 1),
                ),
            )
            val state = anAppState(
                currentPhase = TurnPhase.MOVEMENT,
                gameState = gameState,
                turnState = turnState,
            )

            val result = handlePhaseOutcome(PhaseOutcome.Complete(newGameState), state)

            // Should stay in MOVEMENT since P2 still needs to move
            assertEquals(TurnPhase.MOVEMENT, result.currentPhase)
            assertTrue(UnitId("u1") in result.turnState!!.movedUnitIds)
            assertEquals(PlayerId.PLAYER_2, result.turnState!!.activePlayer)
        }

        @Test
        fun `Complete during movement advances to weapon attack when all impulses done`() {
            val unit = aUnit(id = "u1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
            val gameState = aGameState(units = listOf(unit))
            val newGameState = aGameState(units = listOf(unit.copy(position = HexCoordinates(1, 1))))
            val turnState = aTurnState(
                movementOrder = listOf(MovementImpulse(PlayerId.PLAYER_1, 1)),
            )
            val state = anAppState(
                currentPhase = TurnPhase.MOVEMENT,
                gameState = gameState,
                turnState = turnState,
            )

            val result = handlePhaseOutcome(PhaseOutcome.Complete(newGameState), state)

            assertEquals(TurnPhase.WEAPON_ATTACK, result.currentPhase)
        }

        @Test
        fun `Complete without turnState advances phase directly`() {
            val newGameState = aGameState(units = listOf(aUnit(position = HexCoordinates(5, 5))))
            val state = anAppState(currentPhase = TurnPhase.WEAPON_ATTACK)

            val result = handlePhaseOutcome(PhaseOutcome.Complete(newGameState), state)

            assertEquals(TurnPhase.PHYSICAL_ATTACK, result.currentPhase)
            assertEquals(PhaseState.Idle(), result.phaseState)
        }

        @Test
        fun `Cancelled resets to Idle`() {
            val browsing = PhaseState.Movement.Browsing(
                unitId = UnitId("u1"),
                modes = emptyList(),
                currentModeIndex = 0,
                hoveredPath = null,
                hoveredDestination = null,
                prompt = "test",
            )
            val state = anAppState(phaseState = browsing)

            val result = handlePhaseOutcome(PhaseOutcome.Cancelled, state)

            assertEquals(PhaseState.Idle(), result.phaseState)
            assertEquals(state.gameState, result.gameState)
        }
    }

    @Nested
    inner class MoveCursorTest {
        @Test
        fun `moves to neighbor within bounds`() {
            val map = aGameMap(cols = 5, rows = 5)
            val result = moveCursor(HexCoordinates(2, 2), HexDirection.N, map)
            assertEquals(HexCoordinates(2, 1), result)
        }

        @Test
        fun `stays in place when out of bounds`() {
            val map = aGameMap(cols = 3, rows = 3)
            val result = moveCursor(HexCoordinates(0, 0), HexDirection.N, map)
            assertEquals(HexCoordinates(0, 0), result)
        }
    }

    @Nested
    inner class AutoAdvanceGlobalPhasesTest {
        @Test
        fun `initiative rolls dice and builds turn state`() {
            val p1 = aUnit(id = "p1", owner = PlayerId.PLAYER_1)
            val p2 = aUnit(id = "p2", owner = PlayerId.PLAYER_2, position = HexCoordinates(1, 0))
            val gameState = aGameState(units = listOf(p1, p2))
            val state = anAppState(currentPhase = TurnPhase.INITIATIVE, gameState = gameState)

            val (result, flash) = autoAdvanceGlobalPhases(state, Random(42))

            assertEquals(TurnPhase.MOVEMENT, result.currentPhase)
            assertNotNull(result.turnState)
            assertNotNull(flash)
            assertTrue(flash!!.text.startsWith("Initiative:"))
        }

        @Test
        fun `heat phase applies dissipation to all units`() {
            val unit1 = aUnit(id = "u1", name = "Atlas").copy(currentHeat = 12, heatSinkCapacity = 3)
            val unit2 = aUnit(id = "u2", name = "Hunchback").copy(currentHeat = 5, heatSinkCapacity = 5)
            val gameState = aGameState(units = listOf(unit1, unit2))
            val state = anAppState(currentPhase = TurnPhase.HEAT, gameState = gameState)

            val (result, flash) = autoAdvanceGlobalPhases(state)

            assertEquals(TurnPhase.END, result.currentPhase)
            assertEquals(9, result.gameState.units[0].currentHeat)
            assertEquals(0, result.gameState.units[1].currentHeat)
            assertNotNull(flash)
            assert(flash!!.text.contains("Atlas: 12→9"))
            assert(flash.text.contains("Hunchback: 5→0"))
        }

        @Test
        fun `heat phase with no heat shows no heat message`() {
            val unit = aUnit(id = "u1").copy(currentHeat = 0, heatSinkCapacity = 3)
            val gameState = aGameState(units = listOf(unit))
            val state = anAppState(currentPhase = TurnPhase.HEAT, gameState = gameState)

            val (_, flash) = autoAdvanceGlobalPhases(state)

            assertEquals("Heat: No heat to dissipate", flash!!.text)
        }

        @Test
        fun `end phase advances to initiative`() {
            val state = anAppState(currentPhase = TurnPhase.END)

            val (result, flash) = autoAdvanceGlobalPhases(state)

            assertEquals(TurnPhase.INITIATIVE, result.currentPhase)
            assertEquals("Turn complete", flash!!.text)
        }

        @Test
        fun `interactive phases return null flash`() {
            val state = anAppState(currentPhase = TurnPhase.MOVEMENT)

            val (result, flash) = autoAdvanceGlobalPhases(state)

            assertNull(flash)
            assertEquals(state, result)
        }
    }

    @Nested
    inner class ApplyHeatDissipationTest {
        @Test
        fun `reduces heat by sink capacity`() {
            val unit = aUnit().copy(currentHeat = 10, heatSinkCapacity = 4)
            val gameState = aGameState(units = listOf(unit))

            val result = applyHeatDissipation(gameState)

            assertEquals(6, result.units[0].currentHeat)
        }

        @Test
        fun `heat does not go below zero`() {
            val unit = aUnit().copy(currentHeat = 2, heatSinkCapacity = 10)
            val gameState = aGameState(units = listOf(unit))

            val result = applyHeatDissipation(gameState)

            assertEquals(0, result.units[0].currentHeat)
        }

        @Test
        fun `applies to all units`() {
            val u1 = aUnit(id = "u1").copy(currentHeat = 8, heatSinkCapacity = 3)
            val u2 = aUnit(id = "u2").copy(currentHeat = 4, heatSinkCapacity = 4)
            val gameState = aGameState(units = listOf(u1, u2))

            val result = applyHeatDissipation(gameState)

            assertEquals(5, result.units[0].currentHeat)
            assertEquals(0, result.units[1].currentHeat)
        }
    }
}
