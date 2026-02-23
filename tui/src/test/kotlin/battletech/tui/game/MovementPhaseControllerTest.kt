package battletech.tui.game

import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.input.InputAction
import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.TurnPhase
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachableHex
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.model.MovementMode
import io.mockk.every
import io.mockk.mockk
import battletech.tactical.action.PhaseActionReport
import battletech.tactical.action.AvailableAction
import battletech.tactical.action.ActionId
import battletech.tactical.action.Warning
import battletech.tactical.action.movement.MovementPreview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class MovementPhaseControllerTest {

    private val reachableHexes = listOf(
        ReachableHex(
            position = HexCoordinates(1, 0),
            facing = HexDirection.N,
            mpSpent = 1,
            path = listOf(
                MovementStep(HexCoordinates(0, 0), HexDirection.N),
                MovementStep(HexCoordinates(1, 0), HexDirection.N),
            ),
        ),
        ReachableHex(
            position = HexCoordinates(2, 0),
            facing = HexDirection.N,
            mpSpent = 2,
            path = listOf(
                MovementStep(HexCoordinates(0, 0), HexDirection.N),
                MovementStep(HexCoordinates(1, 0), HexDirection.N),
                MovementStep(HexCoordinates(2, 0), HexDirection.N),
            ),
        ),
        ReachableHex(
            position = HexCoordinates(1, 0),
            facing = HexDirection.SE,
            mpSpent = 2,
            path = listOf(
                MovementStep(HexCoordinates(0, 0), HexDirection.N),
                MovementStep(HexCoordinates(1, 0), HexDirection.SE),
            ),
        ),
    )

    private val reachabilityMap = ReachabilityMap(
        mode = MovementMode.WALK,
        maxMP = 4,
        destinations = reachableHexes,
    )

    private fun createController(): MovementPhaseController {
        val actionQueryService = mockk<ActionQueryService>()
        every { actionQueryService.getMovementActions(any(), any()) } returns PhaseActionReport(
            phase = TurnPhase.MOVEMENT,
            unitId = aUnit().id,
            actions = listOf(
                AvailableAction(
                    id = ActionId("walk"),
                    name = "Walk",
                    successChance = 100,
                    warnings = emptyList(),
                    preview = MovementPreview(reachabilityMap),
                ),
            ),
        )
        return MovementPhaseController(actionQueryService)
    }

    @Test
    fun `enter produces phase state with reachability`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))

        val phaseState = controller.enter(unit, gameState)

        assertEquals(TurnPhase.MOVEMENT, phaseState.phase)
        assertNotNull(phaseState.reachability)
        assertEquals(3, phaseState.reachability!!.destinations.size)
    }

    @Test
    fun `cursor on reachable hex extracts cheapest path`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState)

        val result = controller.handleAction(
            InputAction.ClickHex(HexCoordinates(1, 0)),
            phaseState,
            gameState,
        )

        val updated = (result as PhaseControllerResult.UpdateState).phaseState
        // Should pick the cheapest (mpSpent=1) path to (1,0)
        assertNotNull(updated.highlightedPath)
        assertEquals(
            listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
            updated.highlightedPath,
        )
    }

    @Test
    fun `cursor on unreachable hex clears path`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState)

        val result = controller.handleAction(
            InputAction.ClickHex(HexCoordinates(5, 5)),
            phaseState,
            gameState,
        )

        val updated = (result as PhaseControllerResult.UpdateState).phaseState
        assertNull(updated.highlightedPath)
    }

    @Test
    fun `confirm on reachable hex selects destination`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState).copy(
            selectedDestination = reachableHexes[0],
        )

        val result = controller.handleAction(InputAction.Confirm, phaseState, gameState)

        assert(result is PhaseControllerResult.Complete)
    }

    @Test
    fun `confirm persists facing from destination`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val destination = reachableHexes[2] // facing = SE
        val phaseState = controller.enter(unit, gameState).copy(
            selectedDestination = destination,
        )

        val result = controller.handleAction(InputAction.Confirm, phaseState, gameState)

        val complete = result as PhaseControllerResult.Complete
        val movedUnit = complete.updatedGameState.units.first { it.id == unit.id }
        assertEquals(HexDirection.SE, movedUnit.facing)
    }

    @Test
    fun `cancel returns cancelled`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState)

        val result = controller.handleAction(InputAction.Cancel, phaseState, gameState)

        assert(result is PhaseControllerResult.Cancelled)
    }
}
