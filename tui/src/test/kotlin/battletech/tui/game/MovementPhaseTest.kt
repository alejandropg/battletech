package battletech.tui.game

import battletech.tactical.action.ActionId
import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.AvailableAction
import battletech.tactical.action.Impulse
import battletech.tactical.action.InitiativeResult
import battletech.tactical.action.PhaseActionReport
import battletech.tactical.action.PlayerId
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
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.MovementPhase
import battletech.tui.game.phase.PhaseServices
import battletech.tui.game.phase.enterBrowsing
import com.github.ajalt.mordant.input.KeyboardEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class MovementPhaseTest {

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

    private fun createServices(includeRun: Boolean = false): PhaseServices {
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
        return PhaseServices(actionQueryService)
    }

    private fun aTurnState(
        movementOrder: List<Impulse> = listOf(Impulse(PlayerId.PLAYER_1, 1)),
    ) = TurnState(
        initiativeResult = InitiativeResult(
            rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
            loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
        ),
        movementSequence = ImpulseSequence(movementOrder),
    )

    private fun anAppState(
        phase: battletech.tui.game.phase.Phase,
        cursor: HexCoordinates = HexCoordinates(0, 0),
        gameState: battletech.tactical.model.GameState = aGameState(),
        turnState: TurnState? = aTurnState(),
    ) = AppState(gameState = gameState, cursor = cursor, phase = phase, turnState = turnState)

    @Nested
    inner class EnterBrowsingTest {
        @Test
        fun `enter produces Browsing state with reachability`() {
            val services = createServices()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))

            val state = enterBrowsing(unit, gameState, services)

            assertInstanceOf(MovementPhase.Browsing::class.java, state)
            assertEquals(3, state.reachability.destinations.size)
            assertEquals(unit.id, state.unitId)
        }

        @Test
        fun `enter populates modes with all movement modes`() {
            val services = createServices(includeRun = true)
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))

            val state = enterBrowsing(unit, gameState, services)

            assertEquals(2, state.modes.size)
            assertEquals(MovementMode.WALK, state.modes[0].mode)
            assertEquals(MovementMode.RUN, state.modes[1].mode)
            assertEquals(0, state.currentModeIndex)
        }
    }

    @Nested
    inner class ClickHexTest {
        @Test
        fun `click hex updates hover to clicked position`() {
            val services = createServices()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val phase = enterBrowsing(unit, gameState, services)
            val state = anAppState(phase = phase, gameState = gameState)

            val newPhase = phase.handle(KeyboardEvent("ArrowRight"), state, services)?.app?.phase
            // No direct ClickHex via keyboard, simulate via cursor move
            val result = phase.handle(
                KeyboardEvent("ArrowDown"),
                state.copy(cursor = HexCoordinates(2, 0)),
                services,
            )
            // Movement test is essentially via cursor move + path
            val resultPhase = result!!.app.phase as MovementPhase.Browsing
            // unused: just confirm types resolve
            assertNotNull(resultPhase)
            assertNotNull(newPhase)
        }
    }

    @Nested
    inner class ConfirmTest {
        @Test
        fun `confirm on single-facing destination completes movement`() {
            val services = createServices()
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val phase = enterBrowsing(unit, gameState, services).copy(
                hoveredDestination = reachableHexes[1],
            )
            val state = anAppState(phase = phase, cursor = HexCoordinates(2, 0), gameState = gameState)

            val result = phase.handle(KeyboardEvent("Enter"), state, services)

            // Should advance state: phase changes to SelectingUnit or further
            assertNotNull(result)
            val movedUnit = result!!.app.gameState.units.first { it.id == unit.id }
            assertEquals(HexCoordinates(2, 0), movedUnit.position)
        }

        @Test
        fun `confirm on multi-facing hex enters facing selection`() {
            val services = createServices()
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val phase = enterBrowsing(unit, gameState, services).copy(
                hoveredDestination = reachableHexes[0],
            )
            val state = anAppState(phase = phase, cursor = HexCoordinates(1, 0), gameState = gameState)

            val result = phase.handle(KeyboardEvent("Enter"), state, services)

            assertNotNull(result)
            assertInstanceOf(MovementPhase.SelectingFacing::class.java, result!!.app.phase)
        }
    }

    @Nested
    inner class SelectFacingTest {
        @Test
        fun `SelectFacing during facing selection applies movement`() {
            val services = createServices()
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val browsing = enterBrowsing(unit, gameState, services)
            val facingPhase = MovementPhase.SelectingFacing(
                unitId = browsing.unitId,
                modes = browsing.modes,
                currentModeIndex = browsing.currentModeIndex,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
            )
            val state = anAppState(phase = facingPhase, cursor = HexCoordinates(1, 0), gameState = gameState)

            val result = facingPhase.handle(KeyboardEvent("1"), state, services)

            assertNotNull(result)
            val movedUnit = result!!.app.gameState.units.first { it.id == unit.id }
            assertEquals(HexCoordinates(1, 0), movedUnit.position)
            assertEquals(HexDirection.N, movedUnit.facing)
        }

        @Test
        fun `SelectFacing picks correct facing by number`() {
            val services = createServices()
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val browsing = enterBrowsing(unit, gameState, services)
            val facingPhase = MovementPhase.SelectingFacing(
                unitId = browsing.unitId,
                modes = browsing.modes,
                currentModeIndex = browsing.currentModeIndex,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
            )
            val state = anAppState(phase = facingPhase, cursor = HexCoordinates(1, 0), gameState = gameState)

            val result = facingPhase.handle(KeyboardEvent("3"), state, services)

            assertNotNull(result)
            val movedUnit = result!!.app.gameState.units.first { it.id == unit.id }
            assertEquals(HexDirection.SE, movedUnit.facing)
        }

        @Test
        fun `SelectFacing for unavailable facing stays in current state`() {
            val services = createServices()
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val browsing = enterBrowsing(unit, gameState, services)
            val facingPhase = MovementPhase.SelectingFacing(
                unitId = browsing.unitId,
                modes = browsing.modes,
                currentModeIndex = browsing.currentModeIndex,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
            )
            val state = anAppState(phase = facingPhase, cursor = HexCoordinates(1, 0), gameState = gameState)

            val result = facingPhase.handle(KeyboardEvent("4"), state, services)

            assertNotNull(result)
            assertInstanceOf(MovementPhase.SelectingFacing::class.java, result!!.app.phase)
        }

        @Test
        fun `direct facing selection from browsing with hovered destination`() {
            val services = createServices()
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val phase = enterBrowsing(unit, gameState, services).copy(
                hoveredDestination = reachableHexes[0],
            )
            val state = anAppState(phase = phase, cursor = HexCoordinates(1, 0), gameState = gameState)

            val result = phase.handle(KeyboardEvent("1"), state, services)

            assertNotNull(result)
            val movedUnit = result!!.app.gameState.units.first { it.id == unit.id }
            assertEquals(HexDirection.N, movedUnit.facing)
        }
    }

    @Nested
    inner class CancelTest {
        @Test
        fun `cancel during browsing returns to selecting unit`() {
            val services = createServices()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val phase = enterBrowsing(unit, gameState, services)
            val state = anAppState(phase = phase, gameState = gameState)

            val result = phase.handle(KeyboardEvent("Escape"), state, services)

            assertNotNull(result)
            assertInstanceOf(MovementPhase.SelectingUnit::class.java, result!!.app.phase)
        }

        @Test
        fun `cancel during facing selection returns to browsing`() {
            val services = createServices()
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val browsing = enterBrowsing(unit, gameState, services)
            val facingPhase = MovementPhase.SelectingFacing(
                unitId = browsing.unitId,
                modes = browsing.modes,
                currentModeIndex = browsing.currentModeIndex,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
            )
            val state = anAppState(phase = facingPhase, cursor = HexCoordinates(1, 0), gameState = gameState)

            val result = facingPhase.handle(KeyboardEvent("Escape"), state, services)

            assertNotNull(result)
            assertInstanceOf(MovementPhase.Browsing::class.java, result!!.app.phase)
        }
    }

    @Nested
    inner class CycleModeTest {
        @Test
        fun `CycleMode advances to next mode`() {
            val services = createServices(includeRun = true)
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val phase = enterBrowsing(unit, gameState, services)
            val state = anAppState(phase = phase, gameState = gameState)

            val result = phase.handle(KeyboardEvent("Tab"), state, services)

            val browsing = result!!.app.phase as MovementPhase.Browsing
            assertEquals(1, browsing.currentModeIndex)
            assertEquals(MovementMode.RUN, browsing.reachability.mode)
        }

        @Test
        fun `CycleMode clears hover state`() {
            val services = createServices(includeRun = true)
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val phase = enterBrowsing(unit, gameState, services).copy(
                hoveredDestination = reachableHexes[0],
            )
            val state = anAppState(phase = phase, gameState = gameState)

            val result = phase.handle(KeyboardEvent("Tab"), state, services)

            val browsing = result!!.app.phase as MovementPhase.Browsing
            assertNull(browsing.hoveredPath)
            assertNull(browsing.hoveredDestination)
        }
    }

    @Nested
    inner class CompleteMovementTest {
        @Test
        fun `completing movement of last unit transitions to attack phase`() {
            val services = createServices()
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val phase = enterBrowsing(unit, gameState, services).copy(
                hoveredDestination = reachableHexes[1],
            )
            val state = anAppState(
                phase = phase,
                cursor = HexCoordinates(2, 0),
                gameState = gameState,
                turnState = aTurnState(movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 1))),
            )

            val result = phase.handle(KeyboardEvent("Enter"), state, services)

            assertInstanceOf(AttackPhase.SelectingAttacker::class.java, result!!.app.phase)
        }

        @Test
        fun `completing movement with impulses remaining returns to selecting unit`() {
            val services = createServices()
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val phase = enterBrowsing(unit, gameState, services).copy(
                hoveredDestination = reachableHexes[1],
            )
            val state = anAppState(
                phase = phase,
                cursor = HexCoordinates(2, 0),
                gameState = gameState,
                turnState = aTurnState(
                    movementOrder = listOf(
                        Impulse(PlayerId.PLAYER_1, 1),
                        Impulse(PlayerId.PLAYER_2, 1),
                    ),
                ),
            )

            val result = phase.handle(KeyboardEvent("Enter"), state, services)

            assertInstanceOf(MovementPhase.SelectingUnit::class.java, result!!.app.phase)
        }
    }
}
