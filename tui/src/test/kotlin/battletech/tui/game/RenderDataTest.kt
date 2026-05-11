package battletech.tui.game

import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.Terrain
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex
import battletech.tui.aGameState
import battletech.tui.aUnit
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
    inner class IdlePhaseStateTest {
        @Test
        fun `Idle produces empty render data`() {
            val result = extractRenderData(IdlePhaseState())
            assertEquals(RenderData.EMPTY, result)
        }
    }

    @Nested
    inner class BrowsingTest {
        @Test
        fun `Browsing shows reachability highlights`() {
            val state = MovementPhaseState.Browsing(
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
            val state = MovementPhaseState.Browsing(
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
            val state = MovementPhaseState.Browsing(
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
            val state = MovementPhaseState.Browsing(
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
            val state = MovementPhaseState.SelectingFacing(
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
            val state = MovementPhaseState.SelectingFacing(
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
    inner class AttackPhaseStateTest {
        @Test
        fun `Attack produces arc highlights`() {
            val arcHexes = setOf(HexCoordinates(1, 0), HexCoordinates(2, 0))
            val state = AttackPhaseState(
                unitId = UnitId("u1"),
                attackPhase = battletech.tactical.action.TurnPhase.WEAPON_ATTACK,
                torsoFacing = HexDirection.N,
                arc = arcHexes,
                validTargetIds = emptySet(),
                targets = emptyList(),
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
                weaponAssignments = emptyMap(),
                primaryTargetId = null,
                prompt = "Select attack",
            )

            val result = extractRenderData(state)

            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(1, 0)])
            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(2, 0)])
        }

        @Test
        fun `Attack with empty arc produces empty highlights`() {
            val state = AttackPhaseState(
                unitId = UnitId("u1"),
                attackPhase = battletech.tactical.action.TurnPhase.WEAPON_ATTACK,
                torsoFacing = HexDirection.N,
                arc = emptySet(),
                validTargetIds = emptySet(),
                targets = emptyList(),
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
                weaponAssignments = emptyMap(),
                primaryTargetId = null,
                prompt = "No attacks",
            )

            val result = extractRenderData(state)

            assertEquals(emptyMap<HexCoordinates, HexHighlight>(), result.hexHighlights)
        }

        @Test
        fun `Attack with valid target produces LINE_OF_SIGHT on intervening hexes`() {
            val attacker = aUnit(id = "attacker", position = HexCoordinates(0, 0), owner = PlayerId.PLAYER_1)
            val target = aUnit(id = "target", position = HexCoordinates(0, 3), owner = PlayerId.PLAYER_2)
            val hexes = (0..3).associate { row ->
                HexCoordinates(0, row) to Hex(HexCoordinates(0, row), Terrain.CLEAR)
            }
            val gameState = aGameState(units = listOf(attacker, target), map = GameMap(hexes))
            val arcHexes = setOf(HexCoordinates(0, 1), HexCoordinates(0, 2), HexCoordinates(0, 3))
            val state = AttackPhaseState(
                unitId = UnitId("attacker"),
                attackPhase = battletech.tactical.action.TurnPhase.WEAPON_ATTACK,
                torsoFacing = HexDirection.S,
                arc = arcHexes,
                validTargetIds = setOf(UnitId("target")),
                targets = emptyList(),
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
                weaponAssignments = emptyMap(),
                primaryTargetId = null,
                prompt = "Attack",
            )

            val result = extractRenderData(state, gameState)

            // Intervening hexes get LINE_OF_SIGHT (white)
            assertEquals(HexHighlight.LINE_OF_SIGHT, result.hexHighlights[HexCoordinates(0, 1)])
            assertEquals(HexHighlight.LINE_OF_SIGHT, result.hexHighlights[HexCoordinates(0, 2)])
            // Target hex stays ATTACK_RANGE (gray)
            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(0, 3)])
        }

        @Test
        fun `Attack with target in heavy woods produces no LINE_OF_SIGHT`() {
            val attacker = aUnit(id = "attacker", position = HexCoordinates(0, 0), owner = PlayerId.PLAYER_1)
            val target = aUnit(id = "target", position = HexCoordinates(0, 3), owner = PlayerId.PLAYER_2)
            val hexes = (0..3).associate { row ->
                HexCoordinates(0, row) to Hex(HexCoordinates(0, row), if (row == 3) Terrain.HEAVY_WOODS else Terrain.CLEAR)
            }
            val gameState = aGameState(units = listOf(attacker, target), map = GameMap(hexes))
            val arcHexes = setOf(HexCoordinates(0, 1), HexCoordinates(0, 2), HexCoordinates(0, 3))
            val state = AttackPhaseState(
                unitId = UnitId("attacker"),
                attackPhase = battletech.tactical.action.TurnPhase.WEAPON_ATTACK,
                torsoFacing = HexDirection.S,
                arc = arcHexes,
                validTargetIds = setOf(UnitId("target")),
                targets = emptyList(),
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
                weaponAssignments = emptyMap(),
                primaryTargetId = null,
                prompt = "Attack",
            )

            val result = extractRenderData(state, gameState)

            // No LoS — all arc hexes remain ATTACK_RANGE
            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(0, 1)])
            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(0, 2)])
            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(0, 3)])
        }

        @Test
        fun `Attack with adjacent target produces no LINE_OF_SIGHT intervening hexes`() {
            val attacker = aUnit(id = "attacker", position = HexCoordinates(0, 0), owner = PlayerId.PLAYER_1)
            val target = aUnit(id = "target", position = HexCoordinates(0, 1), owner = PlayerId.PLAYER_2)
            val hexes = mapOf(
                HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0), Terrain.CLEAR),
                HexCoordinates(0, 1) to Hex(HexCoordinates(0, 1), Terrain.CLEAR),
            )
            val gameState = aGameState(units = listOf(attacker, target), map = GameMap(hexes))
            val state = AttackPhaseState(
                unitId = UnitId("attacker"),
                attackPhase = battletech.tactical.action.TurnPhase.WEAPON_ATTACK,
                torsoFacing = HexDirection.S,
                arc = setOf(HexCoordinates(0, 1)),
                validTargetIds = setOf(UnitId("target")),
                targets = emptyList(),
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
                weaponAssignments = emptyMap(),
                primaryTargetId = null,
                prompt = "Attack",
            )

            val result = extractRenderData(state, gameState)

            // No intervening hexes — target hex stays ATTACK_RANGE
            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(0, 1)])
        }
    }
}
