package battletech.tui.game

import battletech.tactical.action.ActionId
import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.AvailableAction
import battletech.tactical.action.PhaseActionReport
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.movement.MovementPreview
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex
import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.input.InputAction
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class MovementControllerTest {

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
    ): MovementController {
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
        return MovementController(actionQueryService)
    }

    @Nested
    inner class EnterTest {
        @Test
        fun `enter produces Browsing state with reachability`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))

            val state = controller.enter(unit, gameState)

            assertTrue(state is PhaseState.Movement.Browsing)
            assertEquals(3, state.reachability.destinations.size)
            assertEquals(unit.id, state.unitId)
        }

        @Test
        fun `enter populates modes with all movement modes`() {
            val controller = createController(includeRun = true)
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))

            val state = controller.enter(unit, gameState)

            assertEquals(2, state.modes.size)
            assertEquals(MovementMode.WALK, state.modes[0].mode)
            assertEquals(MovementMode.RUN, state.modes[1].mode)
            assertEquals(0, state.currentModeIndex)
        }

        @Test
        fun `enter prompt includes mode name and MP`() {
            val controller = createController()
            val unit = aUnit(name = "Atlas")
            val gameState = aGameState(units = listOf(unit))

            val state = controller.enter(unit, gameState)

            assertTrue(state.prompt.contains("Walk"))
            assertTrue(state.prompt.contains("4 MP"))
        }
    }

    @Nested
    inner class ClickHexTest {
        @Test
        fun `click hex updates hover to clicked position`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val state = controller.enter(unit, gameState)

            // Main loop moves cursor to clicked hex before dispatching
            val result = controller.handle(
                InputAction.ClickHex(HexCoordinates(2, 0)),
                state,
                HexCoordinates(2, 0),
                gameState,
            )

            val browsing = (result as PhaseOutcome.Continue).phaseState as PhaseState.Movement.Browsing
            assertNotNull(browsing.hoveredDestination)
            assertEquals(HexCoordinates(2, 0), browsing.hoveredDestination!!.position)
        }

        @Test
        fun `click unreachable hex clears hover state`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val state = controller.enter(unit, gameState)

            val result = controller.handle(
                InputAction.ClickHex(HexCoordinates(5, 5)),
                state,
                HexCoordinates(5, 5),
                gameState,
            )

            val browsing = (result as PhaseOutcome.Continue).phaseState as PhaseState.Movement.Browsing
            assertNull(browsing.hoveredPath)
            assertNull(browsing.hoveredDestination)
        }
    }

    @Nested
    inner class ConfirmTest {
        @Test
        fun `confirm on single-facing destination completes movement`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val state = controller.enter(unit, gameState).copy(
                hoveredDestination = reachableHexes[1], // (2,0) N — single facing
            )

            val result = controller.handle(InputAction.Confirm, state, HexCoordinates(2, 0), gameState)

            assertTrue(result is PhaseOutcome.Complete)
        }

        @Test
        fun `confirm persists facing from destination`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val destination = reachableHexes[1] // (2,0) facing N
            val state = controller.enter(unit, gameState).copy(hoveredDestination = destination)

            val result = controller.handle(InputAction.Confirm, state, HexCoordinates(2, 0), gameState)

            val movedUnit = (result as PhaseOutcome.Complete).gameState.units.first { it.id == unit.id }
            assertEquals(HexDirection.N, movedUnit.facing)
        }

        @Test
        fun `confirm on multi-facing hex enters facing selection`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val state = controller.enter(unit, gameState).copy(
                hoveredDestination = reachableHexes[0], // (1,0) has 2 facings
            )

            val result = controller.handle(InputAction.Confirm, state, HexCoordinates(1, 0), gameState)

            assertTrue(result is PhaseOutcome.Continue)
            val newState = (result as PhaseOutcome.Continue).phaseState
            assertTrue(newState is PhaseState.Movement.SelectingFacing)
        }

        @Test
        fun `confirm during facing selection is no-op`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val browsing = controller.enter(unit, gameState)
            val facingState = PhaseState.Movement.SelectingFacing(
                unitId = browsing.unitId,
                modes = browsing.modes,
                currentModeIndex = browsing.currentModeIndex,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
                path = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
                prompt = "Select facing",
            )

            val result = controller.handle(InputAction.Confirm, facingState, HexCoordinates(1, 0), gameState)

            assertTrue(result is PhaseOutcome.Continue)
            assertEquals(facingState, (result as PhaseOutcome.Continue).phaseState)
        }
    }

    @Nested
    inner class SelectActionTest {
        @Test
        fun `SelectAction during facing selection applies movement`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val browsing = controller.enter(unit, gameState)
            val facingState = PhaseState.Movement.SelectingFacing(
                unitId = browsing.unitId,
                modes = browsing.modes,
                currentModeIndex = browsing.currentModeIndex,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
                path = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
                prompt = "Select facing",
            )

            // Press "1" → N direction
            val result = controller.handle(InputAction.SelectAction(1), facingState, HexCoordinates(1, 0), gameState)

            assertTrue(result is PhaseOutcome.Complete)
            val movedUnit = (result as PhaseOutcome.Complete).gameState.units.first { it.id == unit.id }
            assertEquals(HexCoordinates(1, 0), movedUnit.position)
            assertEquals(HexDirection.N, movedUnit.facing)
        }

        @Test
        fun `SelectAction picks correct facing by number`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val browsing = controller.enter(unit, gameState)
            val facingState = PhaseState.Movement.SelectingFacing(
                unitId = browsing.unitId,
                modes = browsing.modes,
                currentModeIndex = browsing.currentModeIndex,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
                path = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
                prompt = "Select facing",
            )

            // FACING_ORDER: [N, NE, SE, S, SW, NW] → index 3 = SE
            val result = controller.handle(InputAction.SelectAction(3), facingState, HexCoordinates(1, 0), gameState)

            val movedUnit = (result as PhaseOutcome.Complete).gameState.units.first { it.id == unit.id }
            assertEquals(HexDirection.SE, movedUnit.facing)
        }

        @Test
        fun `SelectAction for unavailable facing stays in current state`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val browsing = controller.enter(unit, gameState)
            val facingState = PhaseState.Movement.SelectingFacing(
                unitId = browsing.unitId,
                modes = browsing.modes,
                currentModeIndex = browsing.currentModeIndex,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
                path = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
                prompt = "Select facing",
            )

            // Press "4" → S direction, not available at (1,0)
            val result = controller.handle(InputAction.SelectAction(4), facingState, HexCoordinates(1, 0), gameState)

            assertTrue(result is PhaseOutcome.Continue)
        }

        @Test
        fun `SelectAction outside facing selection does nothing`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val state = controller.enter(unit, gameState) // browsing, not facing selection

            val result = controller.handle(InputAction.SelectAction(1), state, HexCoordinates(0, 0), gameState)

            assertTrue(result is PhaseOutcome.Continue)
        }

        @Test
        fun `direct facing selection from browsing with hovered destination`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val state = controller.enter(unit, gameState).copy(
                hoveredDestination = reachableHexes[0], // (1,0) has 2 facings
            )

            // Press "1" → N direction
            val result = controller.handle(InputAction.SelectAction(1), state, HexCoordinates(1, 0), gameState)

            assertTrue(result is PhaseOutcome.Complete)
            val movedUnit = (result as PhaseOutcome.Complete).gameState.units.first { it.id == unit.id }
            assertEquals(HexDirection.N, movedUnit.facing)
        }
    }

    @Nested
    inner class CancelTest {
        @Test
        fun `cancel during browsing cancels phase`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val state = controller.enter(unit, gameState)

            val result = controller.handle(InputAction.Cancel, state, HexCoordinates(0, 0), gameState)

            assertTrue(result is PhaseOutcome.Cancelled)
        }

        @Test
        fun `cancel during facing selection returns to browsing`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val browsing = controller.enter(unit, gameState)
            val facingState = PhaseState.Movement.SelectingFacing(
                unitId = browsing.unitId,
                modes = browsing.modes,
                currentModeIndex = browsing.currentModeIndex,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
                path = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
                prompt = "Select facing",
            )

            val result = controller.handle(InputAction.Cancel, facingState, HexCoordinates(1, 0), gameState)

            assertTrue(result is PhaseOutcome.Continue)
            val newState = (result as PhaseOutcome.Continue).phaseState
            assertTrue(newState is PhaseState.Movement.Browsing)
        }
    }

    @Nested
    inner class CycleModeTest {
        @Test
        fun `CycleUnit advances to next mode`() {
            val controller = createController(includeRun = true)
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val state = controller.enter(unit, gameState)

            val result = controller.handle(InputAction.CycleUnit, state, HexCoordinates(0, 0), gameState)

            val browsing = (result as PhaseOutcome.Continue).phaseState as PhaseState.Movement.Browsing
            assertEquals(1, browsing.currentModeIndex)
            assertEquals(MovementMode.RUN, browsing.reachability.mode)
        }

        @Test
        fun `CycleUnit wraps around`() {
            val controller = createController(includeRun = true)
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val state = controller.enter(unit, gameState)

            // Cycle twice: WALK → RUN → WALK
            val cycled1 = (controller.handle(InputAction.CycleUnit, state, HexCoordinates(0, 0), gameState)
                as PhaseOutcome.Continue).phaseState as PhaseState.Movement
            val result = controller.handle(InputAction.CycleUnit, cycled1, HexCoordinates(0, 0), gameState)

            val browsing = (result as PhaseOutcome.Continue).phaseState as PhaseState.Movement.Browsing
            assertEquals(0, browsing.currentModeIndex)
            assertEquals(MovementMode.WALK, browsing.reachability.mode)
        }

        @Test
        fun `CycleUnit clears hover state`() {
            val controller = createController(includeRun = true)
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val state = controller.enter(unit, gameState).copy(
                hoveredPath = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
                hoveredDestination = reachableHexes[0],
            )

            val result = controller.handle(InputAction.CycleUnit, state, HexCoordinates(0, 0), gameState)

            val browsing = (result as PhaseOutcome.Continue).phaseState as PhaseState.Movement.Browsing
            assertNull(browsing.hoveredPath)
            assertNull(browsing.hoveredDestination)
        }

        @Test
        fun `prompt includes run to-hit penalty`() {
            val controller = createController(includeRun = true)
            val unit = aUnit(name = "Atlas")
            val gameState = aGameState(units = listOf(unit))
            val state = controller.enter(unit, gameState)

            val result = controller.handle(InputAction.CycleUnit, state, HexCoordinates(0, 0), gameState)

            val browsing = (result as PhaseOutcome.Continue).phaseState as PhaseState.Movement.Browsing
            assertTrue(browsing.prompt.contains("Run"))
            assertTrue(browsing.prompt.contains("6 MP"))
            assertTrue(browsing.prompt.contains("+2 to-hit"))
        }
    }

    @Nested
    inner class MoveCursorTest {
        @Test
        fun `MoveCursor updates path to cursor position`() {
            val controller = createController()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val state = controller.enter(unit, gameState)

            val result = controller.handle(
                InputAction.MoveCursor(HexDirection.N),
                state,
                HexCoordinates(2, 0), // cursor is at (2,0) which is reachable
                gameState,
            )

            val browsing = (result as PhaseOutcome.Continue).phaseState as PhaseState.Movement.Browsing
            assertNotNull(browsing.hoveredPath)
            assertNotNull(browsing.hoveredDestination)
            assertEquals(HexCoordinates(2, 0), browsing.hoveredDestination!!.position)
        }
    }

    @Nested
    inner class FacingOrderTest {
        @Test
        fun `FACING_ORDER is clockwise from N`() {
            assertEquals(
                listOf(HexDirection.N, HexDirection.NE, HexDirection.SE, HexDirection.S, HexDirection.SW, HexDirection.NW),
                MovementController.FACING_ORDER,
            )
        }
    }
}
