package battletech.tui.game

import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.hex.HexHighlight
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
import battletech.tactical.action.movement.MovementPreview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class MovementPhaseControllerTest {

    // (1,0) has two facings: N (cheap) and SE (expensive)
    // (2,0) has one facing: N
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
    fun `click hex with single facing auto-applies movement immediately`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState)

        // (2,0) has only one facing (N)
        val result = controller.handleAction(
            InputAction.ClickHex(HexCoordinates(2, 0)),
            phaseState,
            gameState,
        )

        assert(result is PhaseControllerResult.Complete) { "Expected Complete, got $result" }
        val complete = result as PhaseControllerResult.Complete
        val movedUnit = complete.updatedGameState.units.first { it.id == unit.id }
        assertEquals(HexCoordinates(2, 0), movedUnit.position)
        assertEquals(HexDirection.N, movedUnit.facing)
    }

    @Test
    fun `click hex with multiple facings enters facing selection sub-state`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState)

        // (1,0) has two facings: N and SE
        val result = controller.handleAction(
            InputAction.ClickHex(HexCoordinates(1, 0)),
            phaseState,
            gameState,
        )

        val updated = (result as PhaseControllerResult.UpdateState).phaseState
        assertEquals(HexCoordinates(1, 0), updated.facingSelectionHex)
        assertEquals(2, updated.facingOptions.size)
        assertNull(updated.selectedDestination)
        // Path shown for cheapest option
        assertNotNull(updated.highlightedPath)
    }

    @Test
    fun `click hex with multiple facings shows cheapest path`() {
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
        assertEquals(
            listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
            updated.highlightedPath,
        )
    }

    @Test
    fun `click unreachable hex clears selection and facing state`() {
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
        assertNull(updated.facingSelectionHex)
        assertEquals(emptyList<ReachableHex>(), updated.facingOptions)
    }

    @Test
    fun `SelectAction during facing selection applies movement immediately`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        // Manually enter facing selection state
        val phaseState = controller.enter(unit, gameState).copy(
            facingSelectionHex = HexCoordinates(1, 0),
            facingOptions = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
        )

        // Press "1" → N direction
        val result = controller.handleAction(InputAction.SelectAction(1), phaseState, gameState)

        assert(result is PhaseControllerResult.Complete) { "Expected Complete, got $result" }
        val complete = result as PhaseControllerResult.Complete
        val movedUnit = complete.updatedGameState.units.first { it.id == unit.id }
        assertEquals(HexCoordinates(1, 0), movedUnit.position)
        assertEquals(HexDirection.N, movedUnit.facing)
    }

    @Test
    fun `SelectAction during facing selection picks correct facing by number`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState).copy(
            facingSelectionHex = HexCoordinates(1, 0),
            facingOptions = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
        )

        // FACING_ORDER: [N, NE, SE, S, SW, NW] → index 3 = SE
        val result = controller.handleAction(InputAction.SelectAction(3), phaseState, gameState)

        val complete = result as PhaseControllerResult.Complete
        val movedUnit = complete.updatedGameState.units.first { it.id == unit.id }
        assertEquals(HexDirection.SE, movedUnit.facing)
    }

    @Test
    fun `SelectAction for unreachable facing stays in current state`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState).copy(
            facingSelectionHex = HexCoordinates(1, 0),
            facingOptions = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
        )

        // Press "4" → S direction, not available at (1,0)
        val result = controller.handleAction(InputAction.SelectAction(4), phaseState, gameState)

        assert(result is PhaseControllerResult.UpdateState)
    }

    @Test
    fun `SelectAction outside facing selection does nothing`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState) // not in facing selection

        val result = controller.handleAction(InputAction.SelectAction(1), phaseState, gameState)

        assert(result is PhaseControllerResult.UpdateState)
    }

    @Test
    fun `Cancel during facing selection exits sub-state but does not cancel phase`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState).copy(
            facingSelectionHex = HexCoordinates(1, 0),
            facingOptions = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
        )

        val result = controller.handleAction(InputAction.Cancel, phaseState, gameState)

        val updated = (result as PhaseControllerResult.UpdateState).phaseState
        assertNull(updated.facingSelectionHex)
        assertEquals(emptyList<ReachableHex>(), updated.facingOptions)
    }

    @Test
    fun `Cancel outside facing selection cancels phase`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState)

        val result = controller.handleAction(InputAction.Cancel, phaseState, gameState)

        assert(result is PhaseControllerResult.Cancelled)
    }

    @Test
    fun `confirm on reachable hex selects destination`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState).copy(
            selectedDestination = reachableHexes[1], // (2,0) has only one facing: N
        )

        val result = controller.handleAction(InputAction.Confirm, phaseState, gameState)

        assert(result is PhaseControllerResult.Complete)
    }

    @Test
    fun `confirm persists facing from destination`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val destination = reachableHexes[1] // (2,0) facing N
        val phaseState = controller.enter(unit, gameState).copy(
            selectedDestination = destination,
        )

        val result = controller.handleAction(InputAction.Confirm, phaseState, gameState)

        val complete = result as PhaseControllerResult.Complete
        val movedUnit = complete.updatedGameState.units.first { it.id == unit.id }
        assertEquals(HexDirection.N, movedUnit.facing)
    }

    @Test
    fun `Confirm on hex with multiple facings enters facing selection`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState).copy(
            selectedDestination = reachableHexes[0], // (1,0) has 2 facings: N and SE
        )

        val result = controller.handleAction(InputAction.Confirm, phaseState, gameState)

        val updated = (result as PhaseControllerResult.UpdateState).phaseState
        assertEquals(HexCoordinates(1, 0), updated.facingSelectionHex)
        assertEquals(2, updated.facingOptions.size)
        assertNull(updated.selectedDestination)
        assertNotNull(updated.highlightedPath)
    }

    @Test
    fun `Confirm during facing selection is no-op`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState).copy(
            facingSelectionHex = HexCoordinates(1, 0),
            facingOptions = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
        )

        val result = controller.handleAction(InputAction.Confirm, phaseState, gameState)

        val updated = (result as PhaseControllerResult.UpdateState).phaseState
        assertEquals(phaseState, updated)
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
    fun `cycleMode clears highlighted path, selected destination, and facing selection`() {
        val controller = createController(includeRun = true)
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState).copy(
            highlightedPath = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
            selectedDestination = reachableHexes[0],
            facingSelectionHex = HexCoordinates(1, 0),
            facingOptions = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
        )

        val cycled = controller.cycleMode(phaseState, unit.name)

        assertNull(cycled.highlightedPath)
        assertNull(cycled.selectedDestination)
        assertNull(cycled.facingSelectionHex)
        assertEquals(emptyList<ReachableHex>(), cycled.facingOptions)
    }

    @Test
    fun `updatePathForCursor clears facing selection`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))
        val phaseState = controller.enter(unit, gameState).copy(
            facingSelectionHex = HexCoordinates(1, 0),
            facingOptions = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
        )

        val updated = controller.updatePathForCursor(HexCoordinates(2, 0), phaseState)

        assertNull(updated.facingSelectionHex)
        assertEquals(emptyList<ReachableHex>(), updated.facingOptions)
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

    @Test
    fun `facingsByPosition in PhaseState delegates to reachability`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = aGameState(units = listOf(unit))

        val phaseState = controller.enter(unit, gameState)
        val facings = phaseState.facingsByPosition

        assertEquals(setOf(HexDirection.N, HexDirection.SE), facings[HexCoordinates(1, 0)])
        assertEquals(setOf(HexDirection.N), facings[HexCoordinates(2, 0)])
    }

    @Test
    fun `hexHighlights excludes destination from PATH highlight`() {
        val path = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0), HexCoordinates(2, 0))
        val phaseState = PhaseState(
            phase = TurnPhase.MOVEMENT,
            selectedUnitId = null,
            reachability = walkReachabilityMap,
            highlightedPath = path,
        )

        val highlights = phaseState.hexHighlights()

        assertEquals(HexHighlight.PATH, highlights[HexCoordinates(0, 0)])
        assertEquals(HexHighlight.PATH, highlights[HexCoordinates(1, 0)])
        assertEquals(HexHighlight.REACHABLE_WALK, highlights[HexCoordinates(2, 0)])
    }

    @Test
    fun `FACING_ORDER is clockwise from N`() {
        assertEquals(
            listOf(HexDirection.N, HexDirection.NE, HexDirection.SE, HexDirection.S, HexDirection.SW, HexDirection.NW),
            MovementPhaseController.FACING_ORDER,
        )
    }
}
