package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.attack.definition.FireWeaponActionDefinition
import battletech.tactical.action.movement.MoveActionDefinition
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

    private val actionQueryService = ActionQueryService(
        MoveActionDefinition(),
        listOf(FireWeaponActionDefinition()),
    )
    private val phaseManager = PhaseManager(
        movementController = MovementController(actionQueryService),
        attackController = AttackController(),
        random = Random(42),
    )

    private fun anAppState(
        currentPhase: TurnPhase = TurnPhase.MOVEMENT,
        phase: PhaseState = IdlePhaseState,
        cursor: HexCoordinates = HexCoordinates(0, 0),
        gameState: battletech.tactical.model.GameState = aGameState(),
        turnState: TurnState? = null,
    ) = AppState(
        gameState = gameState,
        currentPhase = currentPhase,
        cursor = cursor,
        phase = phase,
        turnState = turnState,
    )

    @Nested
    inner class NextPhaseTest {
        @Test
        fun `movement advances to weapon attack`() {
            assertEquals(TurnPhase.WEAPON_ATTACK, TurnPhase.MOVEMENT.next)
        }

        @Test
        fun `weapon attack advances to physical attack`() {
            assertEquals(TurnPhase.PHYSICAL_ATTACK, TurnPhase.WEAPON_ATTACK.next)
        }

        @Test
        fun `end advances to initiative`() {
            assertEquals(TurnPhase.INITIATIVE, TurnPhase.END.next)
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
                current = current.next
                visited.add(current)
            }
            assertEquals(phases + TurnPhase.INITIATIVE, visited)
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

            val (result, flash) = phaseManager.advanceAutomaticPhases(state)

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

            val (result, flash) = phaseManager.advanceAutomaticPhases(state)

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

            val (_, flash) = phaseManager.advanceAutomaticPhases(state)

            assertEquals("Heat: No heat to dissipate", flash!!.text)
        }

        @Test
        fun `end phase advances to initiative`() {
            val state = anAppState(currentPhase = TurnPhase.END)

            val (result, flash) = phaseManager.advanceAutomaticPhases(state)

            assertEquals(TurnPhase.INITIATIVE, result.currentPhase)
            assertEquals("Turn complete", flash!!.text)
        }

        @Test
        fun `interactive phases return null flash`() {
            val state = anAppState(currentPhase = TurnPhase.MOVEMENT)

            val (result, flash) = phaseManager.advanceAutomaticPhases(state)

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
