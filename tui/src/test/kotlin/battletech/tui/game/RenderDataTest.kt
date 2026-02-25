package battletech.tui.game

import battletech.tactical.action.UnitId
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex
import battletech.tui.hex.HexHighlight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class RenderDataTest {

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
    )

    private val walkReachability = ReachabilityMap(
        mode = MovementMode.WALK,
        maxMP = 4,
        destinations = reachableHexes,
    )

    private val runReachability = ReachabilityMap(
        mode = MovementMode.RUN,
        maxMP = 6,
        destinations = reachableHexes,
    )

    @Nested
    inner class IdleTest {
        @Test
        fun `Idle produces empty render data`() {
            val result = extractRenderData(PhaseState.Idle())
            assertEquals(RenderData.EMPTY, result)
        }
    }

    @Nested
    inner class BrowsingTest {
        @Test
        fun `Browsing shows reachability highlights`() {
            val state = PhaseState.Movement.Browsing(
                unitId = UnitId("u1"),
                modes = listOf(walkReachability),
                currentModeIndex = 0,
                hoveredPath = null,
                hoveredDestination = null,
                prompt = "Walk",
            )

            val result = extractRenderData(state)

            assertEquals(HexHighlight.REACHABLE_WALK, result.hexHighlights[HexCoordinates(1, 0)])
            assertEquals(HexHighlight.REACHABLE_WALK, result.hexHighlights[HexCoordinates(2, 0)])
        }

        @Test
        fun `Browsing with hovered path shows path highlights`() {
            val path = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0), HexCoordinates(2, 0))
            val state = PhaseState.Movement.Browsing(
                unitId = UnitId("u1"),
                modes = listOf(walkReachability),
                currentModeIndex = 0,
                hoveredPath = path,
                hoveredDestination = reachableHexes[1],
                prompt = "Walk",
            )

            val result = extractRenderData(state)

            assertEquals(HexHighlight.PATH, result.hexHighlights[HexCoordinates(0, 0)])
            assertEquals(HexHighlight.PATH, result.hexHighlights[HexCoordinates(1, 0)])
            // Destination is NOT path-highlighted (reachability highlight instead)
            assertEquals(HexHighlight.REACHABLE_WALK, result.hexHighlights[HexCoordinates(2, 0)])
        }

        @Test
        fun `Browsing with run mode shows run highlights`() {
            val state = PhaseState.Movement.Browsing(
                unitId = UnitId("u1"),
                modes = listOf(runReachability),
                currentModeIndex = 0,
                hoveredPath = null,
                hoveredDestination = null,
                prompt = "Run",
            )

            val result = extractRenderData(state)

            assertEquals(HexHighlight.REACHABLE_RUN, result.hexHighlights[HexCoordinates(1, 0)])
        }

        @Test
        fun `Browsing provides reachable facings`() {
            val state = PhaseState.Movement.Browsing(
                unitId = UnitId("u1"),
                modes = listOf(walkReachability),
                currentModeIndex = 0,
                hoveredPath = null,
                hoveredDestination = null,
                prompt = "Walk",
            )

            val result = extractRenderData(state)

            assertNotNull(result.reachableFacings[HexCoordinates(1, 0)])
            assertNull(result.facingSelection)
        }
    }

    @Nested
    inner class SelectingFacingTest {
        @Test
        fun `SelectingFacing includes facing selection data`() {
            val options = reachableHexes.filter { it.position == HexCoordinates(1, 0) }
            val state = PhaseState.Movement.SelectingFacing(
                unitId = UnitId("u1"),
                modes = listOf(walkReachability),
                currentModeIndex = 0,
                hex = HexCoordinates(1, 0),
                options = options,
                path = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
                prompt = "Select facing",
            )

            val result = extractRenderData(state)

            assertNotNull(result.facingSelection)
            assertEquals(HexCoordinates(1, 0), result.facingSelection!!.hex)
            assertEquals(setOf(HexDirection.N), result.facingSelection!!.facings)
        }

        @Test
        fun `SelectingFacing shows path highlights`() {
            val state = PhaseState.Movement.SelectingFacing(
                unitId = UnitId("u1"),
                modes = listOf(walkReachability),
                currentModeIndex = 0,
                hex = HexCoordinates(1, 0),
                options = reachableHexes,
                path = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
                prompt = "Select facing",
            )

            val result = extractRenderData(state)

            assertEquals(HexHighlight.PATH, result.hexHighlights[HexCoordinates(0, 0)])
        }
    }

    @Nested
    inner class AttackTest {
        @Test
        fun `Attack produces empty render data`() {
            val state = PhaseState.Attack(
                unitId = UnitId("u1"),
                attackPhase = battletech.tactical.action.TurnPhase.WEAPON_ATTACK,
                prompt = "Select attack",
            )

            val result = extractRenderData(state)

            assertEquals(RenderData.EMPTY, result)
        }
    }
}
