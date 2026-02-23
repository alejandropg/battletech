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

    private val walkReachabilityMap = ReachabilityMap(
        mode = MovementMode.WALK,
        maxMP = 4,
        destinations = reachableHexes,
    )

    private val runReachabilityMap = ReachabilityMap(
        mode = MovementMode.RUN,
        maxMP = 6,
        destinations = reachableHexes + listOf(
            ReachableHex(
                position = HexCoordinates(3, 0),
                facing = HexDirection.N,
                mpSpent = 3,
                path = listOf(
                    MovementStep(HexCoordinates(0, 0), HexDirection.N),
                    MovementStep(HexCoordinates(1, 0), HexDirection.N),
                    MovementStep(HexCoordinates(2, 0), HexDirection.N),
                    MovementStep(HexCoordinates(3, 0), HexDirection.N),
                ),
            ),
        ),
    )

    private fun createController(
        includeRun: Boolean = false,
    ): MovementPhaseController {
        val actions = buildList {
            add(
                AvailableAction(
                    id = ActionId("walk"),
                    name = "Walk",
                    successChance = 100,
                    warnings = emptyList(),
                    preview = MovementPreview(walkReachabilityMap),
                ),
            )
            if (includeRun) {
                add(
                    AvailableAction(
                        id = ActionId("run"),
                        name = "Run",
                        successChance = 100,
                        warnings = emptyList(),
                        preview = MovementPreview(runReachabilityMap),
                    ),
                )
            }
        }
        val actionQueryService = mockk<ActionQueryService>()
        every { actionQueryService.getMovementActions(any(), any()) } returns PhaseActionReport(
            phase = TurnPhase.MOVEMENT,
            unitId = aUnit().id,
            actions = actions,
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

    @Test
    fun `enter populates availableModes with all movement modes`() {
        val controller = createController(includeRun = true)
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))

        val phaseState = controller.enter(unit, gameState)

        assertEquals(2, phaseState.availableModes.size)
        assertEquals(MovementMode.WALK, phaseState.availableModes[0].mode)
        assertEquals(MovementMode.RUN, phaseState.availableModes[1].mode)
        assertEquals(0, phaseState.currentModeIndex)
        assertEquals(phaseState.availableModes[0], phaseState.reachability)
    }

    @Test
    fun `cycleMode advances to next mode and updates reachability`() {
        val controller = createController(includeRun = true)
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState)

        val cycled = controller.cycleMode(phaseState, unit.name)

        assertEquals(1, cycled.currentModeIndex)
        assertEquals(MovementMode.RUN, cycled.reachability!!.mode)
        assertEquals(6, cycled.reachability!!.maxMP)
    }

    @Test
    fun `cycleMode wraps around to first mode`() {
        val controller = createController(includeRun = true)
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState)

        val cycledOnce = controller.cycleMode(phaseState, unit.name)
        val cycledTwice = controller.cycleMode(cycledOnce, unit.name)

        assertEquals(0, cycledTwice.currentModeIndex)
        assertEquals(MovementMode.WALK, cycledTwice.reachability!!.mode)
    }

    @Test
    fun `cycleMode clears highlighted path and selected destination`() {
        val controller = createController(includeRun = true)
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState).copy(
            highlightedPath = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
            selectedDestination = reachableHexes[0],
        )

        val cycled = controller.cycleMode(phaseState, unit.name)

        assertNull(cycled.highlightedPath)
        assertNull(cycled.selectedDestination)
    }

    @Test
    fun `prompt includes mode name and MP`() {
        val controller = createController(includeRun = true)
        val unit = aUnit(name = "Atlas")
        val gameState = aGameState(units = listOf(unit))

        val phaseState = controller.enter(unit, gameState)
        assert(phaseState.prompt.contains("Walk"))
        assert(phaseState.prompt.contains("4 MP"))

        val cycled = controller.cycleMode(phaseState, unit.name)
        assert(cycled.prompt.contains("Run"))
        assert(cycled.prompt.contains("6 MP"))
        assert(cycled.prompt.contains("+2 to-hit"))
    }
}
