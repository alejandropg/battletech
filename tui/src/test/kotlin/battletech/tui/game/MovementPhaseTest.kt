package battletech.tui.game

import battletech.tactical.dice.DiceRoll
import battletech.tactical.session.Impulse
import battletech.tactical.session.Initiative
import battletech.tactical.model.PlayerId
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.TurnState
import battletech.tactical.query.DefaultPlayerView
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.MovementPhase
import battletech.tui.game.phase.enterBrowsing
import com.github.ajalt.mordant.input.KeyboardEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    private fun aTurnState(
        movementOrder: List<Impulse> = listOf(Impulse(PlayerId.PLAYER_1, 1)),
    ) = TurnState(
        initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
            loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
        ),
        movement = battletech.tactical.session.MovementProgress(
            sequence = ImpulseSequence(movementOrder),
        ),
    )

    private fun anAppState(
        phase: battletech.tui.game.phase.Phase,
        cursor: HexCoordinates = HexCoordinates(0, 0),
        gameState: battletech.tactical.model.GameState = aGameState(),
        turnState: TurnState = aTurnState(),
    ) = AppState(gameState, turnState, phase, cursor)

    @Nested
    inner class EnterBrowsingTest {
        @Test
        fun `enter produces Browsing state with reachability`() {
            val unit = aUnit(walkingMP = 3)
            val gameState = aGameState(units = listOf(unit))
            val view = DefaultPlayerView(unit.owner, gameState)

            val state = enterBrowsing(unit, view)

            assertInstanceOf(MovementPhase.Browsing::class.java, state)
            assertTrue(state.reachability.destinations.isNotEmpty())
            assertEquals(unit.id, state.unitId)
        }

        @Test
        fun `enter populates modes with all movement modes`() {
            val unit = aUnit(walkingMP = 3, runningMP = 5)
            val gameState = aGameState(units = listOf(unit))
            val view = DefaultPlayerView(unit.owner, gameState)

            val state = enterBrowsing(unit, view)

            assertEquals(2, state.modes.size)
            assertEquals(MovementMode.WALK, state.modes[0].mode)
            assertEquals(MovementMode.RUN, state.modes[1].mode)
            assertEquals(0, state.currentModeIndex)
        }
    }

    @Nested
    inner class ConfirmTest {
        @Test
        fun `confirm on single-facing destination completes movement`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hoveredDestination = reachableHexes[1],
            )
            val state = anAppState(phase = phase, cursor = HexCoordinates(2, 0), gameState = gameState)

            val result = phase.handle(KeyboardEvent("Enter"), state)

            assertNotNull(result)
            val movedUnit = result!!.app.gameState.units.first { it.id == unit.id }
            assertEquals(HexCoordinates(2, 0), movedUnit.position)
        }

        @Test
        fun `confirm on multi-facing hex enters facing selection`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hoveredDestination = reachableHexes[0],
            )
            val state = anAppState(phase = phase, cursor = HexCoordinates(1, 0), gameState = gameState)

            val result = phase.handle(KeyboardEvent("Enter"), state)

            assertNotNull(result)
            assertInstanceOf(MovementPhase.SelectingFacing::class.java, result!!.app.phase)
        }
    }

    @Nested
    inner class SelectFacingTest {
        @Test
        fun `SelectFacing during facing selection applies movement`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val facingPhase = MovementPhase.SelectingFacing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
            )
            val state = anAppState(phase = facingPhase, cursor = HexCoordinates(1, 0), gameState = gameState)

            val result = facingPhase.handle(KeyboardEvent("1"), state)

            assertNotNull(result)
            val movedUnit = result!!.app.gameState.units.first { it.id == unit.id }
            assertEquals(HexCoordinates(1, 0), movedUnit.position)
            assertEquals(HexDirection.N, movedUnit.facing)
        }

        @Test
        fun `SelectFacing picks correct facing by number`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val facingPhase = MovementPhase.SelectingFacing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
            )
            val state = anAppState(phase = facingPhase, cursor = HexCoordinates(1, 0), gameState = gameState)

            val result = facingPhase.handle(KeyboardEvent("3"), state)

            assertNotNull(result)
            val movedUnit = result!!.app.gameState.units.first { it.id == unit.id }
            assertEquals(HexDirection.SE, movedUnit.facing)
        }

        @Test
        fun `SelectFacing for unavailable facing stays in current state`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val facingPhase = MovementPhase.SelectingFacing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
            )
            val state = anAppState(phase = facingPhase, cursor = HexCoordinates(1, 0), gameState = gameState)

            val result = facingPhase.handle(KeyboardEvent("4"), state)

            assertNotNull(result)
            assertInstanceOf(MovementPhase.SelectingFacing::class.java, result!!.app.phase)
        }

        @Test
        fun `direct facing selection from browsing with hovered destination`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hoveredDestination = reachableHexes[0],
            )
            val state = anAppState(phase = phase, cursor = HexCoordinates(1, 0), gameState = gameState)

            val result = phase.handle(KeyboardEvent("1"), state)

            assertNotNull(result)
            val movedUnit = result!!.app.gameState.units.first { it.id == unit.id }
            assertEquals(HexDirection.N, movedUnit.facing)
        }
    }

    @Nested
    inner class CancelTest {
        @Test
        fun `cancel during browsing returns to selecting unit`() {
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hoveredDestination = null,
            )
            val state = anAppState(phase = phase, gameState = gameState)

            val result = phase.handle(KeyboardEvent("Escape"), state)

            assertNotNull(result)
            assertInstanceOf(MovementPhase.SelectingUnit::class.java, result!!.app.phase)
        }

        @Test
        fun `cancel during facing selection returns to browsing`() {
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val facingPhase = MovementPhase.SelectingFacing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
            )
            val state = anAppState(phase = facingPhase, cursor = HexCoordinates(1, 0), gameState = gameState)

            val result = facingPhase.handle(KeyboardEvent("Escape"), state)

            assertNotNull(result)
            assertInstanceOf(MovementPhase.Browsing::class.java, result!!.app.phase)
        }
    }

    @Nested
    inner class CycleModeTest {
        @Test
        fun `CycleMode advances to next mode`() {
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap, runReachabilityMap),
                currentModeIndex = 0,
                hoveredDestination = null,
            )
            val state = anAppState(phase = phase, gameState = gameState)

            val result = phase.handle(KeyboardEvent("x"), state)

            val browsing = result!!.app.phase as MovementPhase.Browsing
            assertEquals(1, browsing.currentModeIndex)
            assertEquals(MovementMode.RUN, browsing.reachability.mode)
        }

        @Test
        fun `CycleMode clears hover state`() {
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap, runReachabilityMap),
                currentModeIndex = 0,
                hoveredDestination = reachableHexes[0],
            )
            val state = anAppState(phase = phase, gameState = gameState)

            val result = phase.handle(KeyboardEvent("x"), state)

            val browsing = result!!.app.phase as MovementPhase.Browsing
            assertNull(browsing.hoveredPath)
            assertNull(browsing.hoveredDestination)
        }
    }

    @Nested
    inner class CycleUnitFromBrowsingTest {
        @Test
        fun `Tab from Browsing advances to next selectable unit`() {
            val u1 = aUnit(id = "u1", position = HexCoordinates(0, 0), walkingMP = 3)
            val u2 = aUnit(id = "u2", position = HexCoordinates(2, 2), walkingMP = 3)
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 5, rows = 5))
            val view = DefaultPlayerView(u1.owner, gameState)
            val phase = enterBrowsing(u1, view)
            val state = anAppState(phase = phase, cursor = HexCoordinates(0, 0), gameState = gameState)

            val result = phase.handle(KeyboardEvent("Tab"), state)

            assertNotNull(result)
            val browsing = result!!.app.phase as MovementPhase.Browsing
            assertEquals(u2.id, browsing.unitId)
        }

        @Test
        fun `Tab from Browsing moves cursor to next unit position`() {
            val u1 = aUnit(id = "u1", position = HexCoordinates(0, 0), walkingMP = 3)
            val u2 = aUnit(id = "u2", position = HexCoordinates(2, 2), walkingMP = 3)
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 5, rows = 5))
            val view = DefaultPlayerView(u1.owner, gameState)
            val phase = enterBrowsing(u1, view)
            val state = anAppState(phase = phase, cursor = HexCoordinates(0, 0), gameState = gameState)

            val result = phase.handle(KeyboardEvent("Tab"), state)

            assertEquals(HexCoordinates(2, 2), result!!.app.cursor)
        }

        @Test
        fun `Tab from Browsing wraps from last unit back to first`() {
            val u1 = aUnit(id = "u1", position = HexCoordinates(0, 0), walkingMP = 3)
            val u2 = aUnit(id = "u2", position = HexCoordinates(2, 2), walkingMP = 3)
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 5, rows = 5))
            val view = DefaultPlayerView(u2.owner, gameState)
            val phase = enterBrowsing(u2, view)
            val state = anAppState(phase = phase, cursor = HexCoordinates(2, 2), gameState = gameState)

            val result = phase.handle(KeyboardEvent("Tab"), state)

            assertNotNull(result)
            val browsing = result!!.app.phase as MovementPhase.Browsing
            assertEquals(u1.id, browsing.unitId)
            assertEquals(HexCoordinates(0, 0), result.app.cursor)
        }

        @Test
        fun `Tab from Browsing with one selectable unit re-enters fresh Browsing`() {
            val u1 = aUnit(id = "u1", position = HexCoordinates(0, 0), walkingMP = 3, runningMP = 5)
            val gameState = aGameState(units = listOf(u1))
            val view = DefaultPlayerView(u1.owner, gameState)
            val phase = enterBrowsing(u1, view).copy(
                currentModeIndex = 1,
                hoveredDestination = reachableHexes[0],
            )
            val state = anAppState(phase = phase, cursor = HexCoordinates(0, 0), gameState = gameState)

            val result = phase.handle(KeyboardEvent("Tab"), state)

            assertNotNull(result)
            val browsing = result!!.app.phase as MovementPhase.Browsing
            assertEquals(u1.id, browsing.unitId)
            assertEquals(0, browsing.currentModeIndex)
            assertNull(browsing.hoveredDestination)
        }

        @Test
        fun `Tab from Browsing does not commit any movement`() {
            val u1 = aUnit(id = "u1", position = HexCoordinates(0, 0), walkingMP = 3)
            val u2 = aUnit(id = "u2", position = HexCoordinates(2, 2), walkingMP = 3)
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 5, rows = 5))
            val turnState = aTurnState()
            val view = DefaultPlayerView(u1.owner, gameState)
            val phase = enterBrowsing(u1, view)
            val state = anAppState(phase = phase, cursor = HexCoordinates(0, 0), gameState = gameState, turnState = turnState)

            val result = phase.handle(KeyboardEvent("Tab"), state)

            assertNotNull(result)
            assertEquals(gameState, result!!.app.gameState)
            assertEquals(turnState.movedUnitIds, result.app.turnState.movedUnitIds)
        }

        @Test
        fun `Tab from SelectingFacing cycles to next unit and enters Browsing`() {
            val u1 = aUnit(id = "u1", position = HexCoordinates(0, 0), walkingMP = 3)
            val u2 = aUnit(id = "u2", position = HexCoordinates(2, 2), walkingMP = 3)
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 5, rows = 5))
            val facingPhase = MovementPhase.SelectingFacing(
                unitId = u1.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
            )
            val state = anAppState(phase = facingPhase, cursor = HexCoordinates(1, 0), gameState = gameState)

            val result = facingPhase.handle(KeyboardEvent("Tab"), state)

            assertNotNull(result)
            val nextBrowsing = result!!.app.phase as MovementPhase.Browsing
            assertEquals(u2.id, nextBrowsing.unitId)
            assertEquals(HexCoordinates(2, 2), result.app.cursor)
        }
    }

    @Nested
    inner class CompleteMovementTest {
        @Test
        fun `completing movement of last unit transitions to attack phase`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hoveredDestination = reachableHexes[1],
            )
            val state = anAppState(
                phase = phase,
                cursor = HexCoordinates(2, 0),
                gameState = gameState,
                turnState = aTurnState(movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 1))),
            )

            val result = phase.handle(KeyboardEvent("Enter"), state)

            assertInstanceOf(AttackPhase.SelectingAttacker::class.java, result!!.app.phase)
        }

        @Test
        fun `completing movement with impulses remaining returns to selecting unit`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit))
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
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

            val result = phase.handle(KeyboardEvent("Enter"), state)

            assertInstanceOf(MovementPhase.SelectingUnit::class.java, result!!.app.phase)
        }
    }
}
