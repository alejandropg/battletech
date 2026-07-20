package battletech.tui.game

import battletech.tactical.session.Impulse
import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.PlayerId
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.MovementRules
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex
import battletech.tactical.query.projectFor
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aTurnState
import battletech.tui.aUnit
import battletech.tui.anAppState
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.MovementPhase
import battletech.tui.game.phase.enterBrowsing
import battletech.tui.game.phase.enterMovementSubMode
import battletech.tui.viewFor
import com.github.ajalt.mordant.input.KeyboardEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class MovementPhaseTest {

    // A map large enough for a 4-MP walk north from (0,0): includes rows -4..4.
    private val bigMap: GameMap = GameMap(
        (-1..1).flatMap { col -> (-4..4).map { row -> HexCoordinates(col, row) } }
            .associateWith { Hex(it) },
    )

    // All three entries are server-computable from (0,0) facing N with walkingMP=4.
    //
    // odd-q, even col (col=0): N-neighbour = (0,-1), NE-neighbour = (1,-1), SE-neighbour = (1,0).
    //
    //  [0] (0,-1) N  : 1 MP  — move N once                     (multi-facing destination)
    //  [1] (0,-2) N  : 2 MP  — move N twice                    (single-facing destination)
    //  [2] (0,-1) SE : 3 MP  — move N, turn CW×NE, turn CW×SE (second facing at [0]'s hex)
    private val reachableHexes = listOf(
        ReachableHex(
            position = HexCoordinates(0, -1),
            facing = HexDirection.N,
            mpSpent = 1,
            path = listOf(MovementStep(HexCoordinates(0, -1), HexDirection.N)),
        ),
        ReachableHex(
            position = HexCoordinates(0, -2),
            facing = HexDirection.N,
            mpSpent = 2,
            path = listOf(
                MovementStep(HexCoordinates(0, -1), HexDirection.N),
                MovementStep(HexCoordinates(0, -2), HexDirection.N),
            ),
        ),
        ReachableHex(
            position = HexCoordinates(0, -1),
            facing = HexDirection.SE,
            mpSpent = 3,
            path = listOf(
                MovementStep(HexCoordinates(0, -1), HexDirection.N),
                MovementStep(HexCoordinates(0, -1), HexDirection.NE),
                MovementStep(HexCoordinates(0, -1), HexDirection.SE),
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
            // (0,-3) N: 3 MP — reachable only when running (6 MP budget).
            ReachableHex(
                position = HexCoordinates(0, -3),
                facing = HexDirection.N,
                mpSpent = 3,
                path = listOf(
                    MovementStep(HexCoordinates(0, -1), HexDirection.N),
                    MovementStep(HexCoordinates(0, -2), HexDirection.N),
                    MovementStep(HexCoordinates(0, -3), HexDirection.N),
                ),
            ),
        ),
    )

    // A turn-in-place option at the unit's own hex (0,0): turning is legal there and shows up
    // as a normal destination — ReachabilityCalculator only ever excludes the unit's exact
    // current position+facing (here, (0,0) facing N), never other facings at that same hex.
    private val turnToNEAtOwnHex = ReachableHex(
        position = HexCoordinates(0, 0),
        facing = HexDirection.NE,
        mpSpent = 1,
        path = listOf(MovementStep(HexCoordinates(0, 0), HexDirection.NE)),
    )

    private val walkWithTurnAtOwnHex = ReachabilityMap(
        mode = MovementMode.WALK,
        maxMP = 4,
        destinations = reachableHexes + turnToNEAtOwnHex,
    )

    private val runWithTurnAtOwnHex = ReachabilityMap(
        mode = MovementMode.RUN,
        maxMP = 6,
        destinations = runReachabilityMap.destinations + turnToNEAtOwnHex,
    )

    // JUMP never offers the unit's own hex at all — ReachabilityCalculator.calculateJump
    // filters it out unconditionally — so a JUMP map has nothing at (0,0); the stationary
    // option `destinationsAt` adds is the only one available there.
    private val jumpReachabilityMap = ReachabilityMap(
        mode = MovementMode.JUMP,
        maxMP = 3,
        destinations = emptyList(),
    )

    @Nested
    inner class EnterBrowsingTest {
        @Test
        fun `enter produces Browsing state with reachability`() {
            val unit = aUnit(walkingMP = 3)
            val gameState = aGameState(units = listOf(unit))
            val view = viewFor(unit.owner, gameState)

            val state = enterBrowsing(unit, view)

            assertInstanceOf(MovementPhase.Browsing::class.java, state)
            assertTrue(state.reachability.destinations.isNotEmpty())
            assertEquals(unit.id, state.unitId)
        }

        @Test
        fun `enter populates modes with all movement modes`() {
            val unit = aUnit(walkingMP = 3, runningMP = 5)
            val gameState = aGameState(units = listOf(unit))
            val view = viewFor(unit.owner, gameState)

            val state = enterBrowsing(unit, view)

            assertEquals(2, state.modes.size)
            assertEquals(MovementMode.WALK, state.modes[0].mode)
            assertEquals(MovementMode.RUN, state.modes[1].mode)
            assertEquals(0, state.currentModeIndex)
        }
    }

    @Nested
    inner class EnterMovementSubModeTest {
        @Test
        fun `entering sub-mode without moving the cursor offers and commits the current facing at the own hex`() {
            val unit = aUnit(id = "u1", position = HexCoordinates(0, 0), walkingMP = 4, runningMP = 6)
            val gameState = aGameState(units = listOf(unit), map = bigMap)
            val state = anAppState(phase = MovementPhase.SelectingUnit, cursor = unit.position, gameState = gameState)

            // The cursor never moves off the unit's own hex — this is exactly the case the
            // old hard-coded `hoveredDestination = null` used to leave stuck until a nudge.
            val entered = enterMovementSubMode(unit, state)
            val browsing = entered.app.phase as MovementPhase.Browsing
            assertEquals(MovementRules.stationaryHex(unit), browsing.hoveredDestination)

            val facingResult = browsing.handle(KeyboardEvent("Enter"), entered.app)

            assertNotNull(facingResult)
            val facingPhase = assertInstanceOf(MovementPhase.SelectingFacing::class.java, facingResult!!.app.phase)
            assertTrue(facingPhase.options.any { it.facing == unit.facing && it.mpSpent == 0 })

            // "1" is FACING_ORDER[0] == N, the unit's current facing.
            val result = facingPhase.handle(KeyboardEvent("1"), facingResult.app)

            assertNotNull(result)
            val movedUnit = result!!.app.visibleState.units.first { it.id == unit.id }
            assertEquals(unit.position, movedUnit.position)
            assertEquals(unit.facing, movedUnit.facing)
        }
    }

    @Nested
    inner class ConfirmTest {
        @Test
        fun `confirm on single-facing destination completes movement`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit), map = bigMap)
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hoveredDestination = reachableHexes[1],
            )
            val state = anAppState(phase = phase, cursor = HexCoordinates(0, -2), gameState = gameState)

            val result = phase.handle(KeyboardEvent("Enter"), state)

            assertNotNull(result)
            val movedUnit = result!!.app.visibleState.units.first { it.id == unit.id }
            assertEquals(HexCoordinates(0, -2), movedUnit.position)
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

        @Test
        fun `confirm on unit's own hex offers facing selection including the current facing`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit), map = bigMap)
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkWithTurnAtOwnHex),
                currentModeIndex = 0,
                hoveredDestination = MovementRules.stationaryHex(unit),
            )
            val state = anAppState(phase = phase, cursor = HexCoordinates(0, 0), gameState = gameState)

            val result = phase.handle(KeyboardEvent("Enter"), state)

            assertNotNull(result)
            val facingPhase = assertInstanceOf(MovementPhase.SelectingFacing::class.java, result!!.app.phase)
            assertTrue(facingPhase.options.any { it.facing == unit.facing && it.mpSpent == 0 && it.path.isEmpty() })
        }

        @Test
        fun `confirm on unit's own hex on the RUN tab also offers the current facing`() {
            val unit = aUnit(walkingMP = 4, runningMP = 6)
            val gameState = aGameState(units = listOf(unit), map = bigMap)
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkWithTurnAtOwnHex, runWithTurnAtOwnHex),
                currentModeIndex = 1,
                hoveredDestination = MovementRules.stationaryHex(unit),
            )
            val state = anAppState(phase = phase, cursor = HexCoordinates(0, 0), gameState = gameState)

            val result = phase.handle(KeyboardEvent("Enter"), state)

            assertNotNull(result)
            val facingPhase = assertInstanceOf(MovementPhase.SelectingFacing::class.java, result!!.app.phase)
            assertTrue(facingPhase.options.any { it.facing == unit.facing && it.mpSpent == 0 })
        }

        @Test
        fun `confirm on unit's own hex on the JUMP tab commits the stationary hex directly`() {
            val unit = aUnit(jumpMP = 3)
            val gameState = aGameState(units = listOf(unit), map = bigMap)
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(jumpReachabilityMap),
                currentModeIndex = 0,
                hoveredDestination = MovementRules.stationaryHex(unit),
            )
            val state = anAppState(phase = phase, cursor = HexCoordinates(0, 0), gameState = gameState)

            val result = phase.handle(KeyboardEvent("Enter"), state)

            assertNotNull(result)
            val movedUnit = result!!.app.visibleState.units.first { it.id == unit.id }
            assertEquals(HexCoordinates(0, 0), movedUnit.position)
            assertEquals(HexDirection.N, movedUnit.facing)
        }
    }

    @Nested
    inner class SelectFacingTest {
        @Test
        fun `SelectFacing during facing selection applies movement`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit), map = bigMap)
            val facingPhase = MovementPhase.SelectingFacing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hex = HexCoordinates(0, -1),
                options = reachableHexes.filter { it.position == HexCoordinates(0, -1) },
            )
            val state = anAppState(phase = facingPhase, cursor = HexCoordinates(0, -1), gameState = gameState)

            val result = facingPhase.handle(KeyboardEvent("1"), state)

            assertNotNull(result)
            val movedUnit = result!!.app.visibleState.units.first { it.id == unit.id }
            assertEquals(HexCoordinates(0, -1), movedUnit.position)
            assertEquals(HexDirection.N, movedUnit.facing)
        }

        @Test
        fun `SelectFacing picks correct facing by number`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit), map = bigMap)
            val facingPhase = MovementPhase.SelectingFacing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hex = HexCoordinates(0, -1),
                options = reachableHexes.filter { it.position == HexCoordinates(0, -1) },
            )
            val state = anAppState(phase = facingPhase, cursor = HexCoordinates(0, -1), gameState = gameState)

            val result = facingPhase.handle(KeyboardEvent("3"), state)

            assertNotNull(result)
            val movedUnit = result!!.app.visibleState.units.first { it.id == unit.id }
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
            val movedUnit = result!!.app.visibleState.units.first { it.id == unit.id }
            assertEquals(HexDirection.N, movedUnit.facing)
        }

        @Test
        fun `SelectFacing for the current facing at own hex commits the stationary hex`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit), map = bigMap)
            val facingPhase = MovementPhase.SelectingFacing(
                unitId = unit.id,
                modes = listOf(walkWithTurnAtOwnHex),
                currentModeIndex = 0,
                hex = HexCoordinates(0, 0),
                options = listOf(turnToNEAtOwnHex, MovementRules.stationaryHex(unit)),
            )
            val state = anAppState(phase = facingPhase, cursor = HexCoordinates(0, 0), gameState = gameState)

            // "1" is FACING_ORDER[0] == N, the unit's current facing.
            val result = facingPhase.handle(KeyboardEvent("1"), state)

            assertNotNull(result)
            val movedUnit = result!!.app.visibleState.units.first { it.id == unit.id }
            assertEquals(HexCoordinates(0, 0), movedUnit.position)
            assertEquals(HexDirection.N, movedUnit.facing)
        }

        @Test
        fun `direct facing selection from browsing for the current facing at own hex commits the stationary hex`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit), map = bigMap)
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkWithTurnAtOwnHex),
                currentModeIndex = 0,
                hoveredDestination = MovementRules.stationaryHex(unit),
            )
            val state = anAppState(phase = phase, cursor = HexCoordinates(0, 0), gameState = gameState)

            val result = phase.handle(KeyboardEvent("1"), state)

            assertNotNull(result)
            val movedUnit = result!!.app.visibleState.units.first { it.id == unit.id }
            assertEquals(HexCoordinates(0, 0), movedUnit.position)
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

        @Test
        fun `cancel during facing selection resolves hover for the cursor`() {
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val facingPhase = MovementPhase.SelectingFacing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hex = HexCoordinates(1, 0),
                options = reachableHexes.filter { it.position == HexCoordinates(1, 0) },
            )
            // Cursor sits on a reachable hex (not the facing-selection target), so onCancel's
            // withCursorAt should resolve a non-null hover there rather than the old hard-coded null.
            val state = anAppState(phase = facingPhase, cursor = HexCoordinates(0, -1), gameState = gameState)

            val result = facingPhase.handle(KeyboardEvent("Escape"), state)

            assertNotNull(result)
            val browsing = result!!.app.phase as MovementPhase.Browsing
            assertEquals(reachableHexes[0], browsing.hoveredDestination)
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
        fun `CycleMode re-resolves hover for the cursor`() {
            val unit = aUnit()
            val gameState = aGameState(units = listOf(unit))
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap, runReachabilityMap),
                currentModeIndex = 0,
                hoveredDestination = reachableHexes[0],
            )
            // Cursor sits on the unit's own hex — after the mode switch, withCursorAt resolves
            // hover there directly instead of leaving it null until the cursor next moves.
            val state = anAppState(phase = phase, cursor = unit.position, gameState = gameState)

            val result = phase.handle(KeyboardEvent("x"), state)

            val browsing = result!!.app.phase as MovementPhase.Browsing
            assertEquals(1, browsing.currentModeIndex)
            assertEquals(MovementRules.stationaryHex(unit), browsing.hoveredDestination)
        }
    }

    @Nested
    inner class HoverOwnHexTest {
        @Test
        fun `moving cursor onto the unit's own hex hovers the stationary hex`() {
            val unit = aUnit(walkingMP = 4)
            val gameState = aGameState(units = listOf(unit), map = bigMap)
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkWithTurnAtOwnHex),
                currentModeIndex = 0,
                hoveredDestination = null,
            )
            // Start one hex away (north of the unit) and move south, landing on (0,0).
            val state = anAppState(phase = phase, cursor = HexCoordinates(0, -1), gameState = gameState)

            val result = phase.handle(KeyboardEvent("ArrowDown"), state)

            assertNotNull(result)
            val browsing = result!!.app.phase as MovementPhase.Browsing
            assertEquals(MovementRules.stationaryHex(unit), browsing.hoveredDestination)
        }

        @Test
        fun `moving cursor onto the unit's own hex on the JUMP tab hovers the stationary hex`() {
            val unit = aUnit(jumpMP = 3)
            val gameState = aGameState(units = listOf(unit), map = bigMap)
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(jumpReachabilityMap),
                currentModeIndex = 0,
                hoveredDestination = null,
            )
            val state = anAppState(phase = phase, cursor = HexCoordinates(0, -1), gameState = gameState)

            val result = phase.handle(KeyboardEvent("ArrowDown"), state)

            assertNotNull(result)
            val browsing = result!!.app.phase as MovementPhase.Browsing
            assertEquals(MovementRules.stationaryHex(unit), browsing.hoveredDestination)
        }
    }

    @Nested
    inner class CycleUnitFromBrowsingTest {
        @Test
        fun `Tab from Browsing advances to next selectable unit`() {
            val u1 = aUnit(id = "u1", position = HexCoordinates(0, 0), walkingMP = 3)
            val u2 = aUnit(id = "u2", position = HexCoordinates(2, 2), walkingMP = 3)
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 5, rows = 5))
            val view = viewFor(u1.owner, gameState)
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
            val view = viewFor(u1.owner, gameState)
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
            val view = viewFor(u2.owner, gameState)
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
            val view = viewFor(u1.owner, gameState)
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
            // The cursor lands back on u1's own hex, so the fresh Browsing re-resolves hover
            // there via withCursorAt instead of leaving it null.
            assertEquals(MovementRules.stationaryHex(u1), browsing.hoveredDestination)
        }

        @Test
        fun `Tab from Browsing does not commit any movement`() {
            val u1 = aUnit(id = "u1", position = HexCoordinates(0, 0), walkingMP = 3)
            val u2 = aUnit(id = "u2", position = HexCoordinates(2, 2), walkingMP = 3)
            val gameState = aGameState(units = listOf(u1, u2), map = aGameMap(cols = 5, rows = 5))
            val turnState = aTurnState()
            val view = viewFor(u1.owner, gameState)
            val phase = enterBrowsing(u1, view)
            val state = anAppState(phase = phase, cursor = HexCoordinates(0, 0), gameState = gameState, turnState = turnState)

            val result = phase.handle(KeyboardEvent("Tab"), state)

            assertNotNull(result)
            assertEquals(gameState.projectFor(result!!.app.viewer), result.app.visibleState)
            assertEquals(turnState.movement.movedUnitIds, result.app.turnState.movement.movedUnitIds)
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
            val gameState = aGameState(units = listOf(unit), map = bigMap)
            val phase = MovementPhase.Browsing(
                unitId = unit.id,
                modes = listOf(walkReachabilityMap),
                currentModeIndex = 0,
                hoveredDestination = reachableHexes[1],
            )
            val state = anAppState(
                phase = phase,
                cursor = HexCoordinates(0, -2),
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
