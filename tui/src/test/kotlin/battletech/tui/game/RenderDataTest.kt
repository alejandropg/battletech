package battletech.tui.game

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.Terrain
import battletech.tactical.model.Weapon
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex
import battletech.tui.aGameMap
import battletech.tui.aGameState
import battletech.tui.aUnit
import battletech.tui.hex.HexHighlight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    private fun mediumLaser(): Weapon = Weapon(
        name = "Medium Laser", damage = 5, heat = 3,
        shortRange = 3, mediumRange = 6, longRange = 9,
    )

    @Nested
    inner class IdlePhaseStateTest {
        @Test
        fun `Idle produces empty render data`() {
            val result = extractRenderData(IdlePhaseState)
            assertEquals(RenderData.EMPTY, result)
        }
    }

    @Nested
    inner class BrowsingTest {
        @Test
        fun `browsing shows reachability highlights`() {
            val state = MovementPhaseState.Browsing(
                unitId = UnitId("u1"),
                modes = listOf(walkReachability),
                currentModeIndex = 0,
                hoveredPath = null,
                hoveredDestination = null,
            )

            val result = extractRenderData(state)

            assertEquals(HexHighlight.REACHABLE_WALK, result.hexHighlights[HexCoordinates(1, 0)])
            assertEquals(HexHighlight.REACHABLE_WALK, result.hexHighlights[HexCoordinates(2, 0)])
        }

        @Test
        fun `browsing with hovered path shows path highlights`() {
            val path = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0), HexCoordinates(2, 0))
            val state = MovementPhaseState.Browsing(
                unitId = UnitId("u1"),
                modes = listOf(walkReachability),
                currentModeIndex = 0,
                hoveredPath = path,
                hoveredDestination = reachableHexes[1],
            )

            val result = extractRenderData(state)

            assertEquals(HexHighlight.PATH, result.hexHighlights[HexCoordinates(0, 0)])
            assertEquals(HexHighlight.PATH, result.hexHighlights[HexCoordinates(1, 0)])
            // Destination is NOT path-highlighted (reachability highlight instead)
            assertEquals(HexHighlight.REACHABLE_WALK, result.hexHighlights[HexCoordinates(2, 0)])
        }

        @Test
        fun `browsing with run mode shows run highlights`() {
            val state = MovementPhaseState.Browsing(
                unitId = UnitId("u1"),
                modes = listOf(runReachability),
                currentModeIndex = 0,
                hoveredPath = null,
                hoveredDestination = null,
            )

            val result = extractRenderData(state)

            assertEquals(HexHighlight.REACHABLE_RUN, result.hexHighlights[HexCoordinates(1, 0)])
        }

        @Test
        fun `browsing provides reachable facings`() {
            val state = MovementPhaseState.Browsing(
                unitId = UnitId("u1"),
                modes = listOf(walkReachability),
                currentModeIndex = 0,
                hoveredPath = null,
                hoveredDestination = null,
            )

            val result = extractRenderData(state)

            assertNotNull(result.reachableFacings[HexCoordinates(1, 0)])
            assertNull(result.facingSelection)
        }
    }

    @Nested
    inner class SelectingFacingTest {
        @Test
        fun `selectingFacing includes facing selection data`() {
            val options = reachableHexes.filter { it.position == HexCoordinates(1, 0) }
            val state = MovementPhaseState.SelectingFacing(
                unitId = UnitId("u1"),
                modes = listOf(walkReachability),
                currentModeIndex = 0,
                hex = HexCoordinates(1, 0),
                options = options,
                path = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
            )

            val result = extractRenderData(state)

            assertNotNull(result.facingSelection)
            assertEquals(HexCoordinates(1, 0), result.facingSelection!!.hex)
            assertEquals(setOf(HexDirection.N), result.facingSelection!!.facings)
        }

        @Test
        fun `selectingFacing shows path highlights`() {
            val state = MovementPhaseState.SelectingFacing(
                unitId = UnitId("u1"),
                modes = listOf(walkReachability),
                currentModeIndex = 0,
                hex = HexCoordinates(1, 0),
                options = reachableHexes,
                path = listOf(HexCoordinates(0, 0), HexCoordinates(1, 0)),
            )

            val result = extractRenderData(state)

            assertEquals(HexHighlight.PATH, result.hexHighlights[HexCoordinates(0, 0)])
        }
    }

    @Nested
    inner class AttackPhaseStateTest {

        private fun attackState(
            attacker: UnitId,
            torsoFacing: HexDirection = HexDirection.S,
            cursorTargetIndex: Int = 0,
        ): AttackPhaseState = AttackPhaseState(
            unitId = attacker,
            attackPhase = TurnPhase.WEAPON_ATTACK,
            torsoFacing = torsoFacing,
            cursorTargetIndex = cursorTargetIndex,
            cursorWeaponIndex = 0,
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
        )

        @Test
        fun `attack produces arc highlights`() {
            val attacker = aUnit(
                id = "u1", owner = PlayerId.PLAYER_1,
                position = HexCoordinates(0, 0), facing = HexDirection.N,
                weapons = listOf(mediumLaser()),
            )
            val gameState = aGameState(units = listOf(attacker), map = aGameMap(cols = 3, rows = 3))
            val arc = fireArc(attacker, HexDirection.N, gameState)
            assertTrue(arc.isNotEmpty())

            val state = attackState(attacker.id, torsoFacing = HexDirection.N)
            val result = extractRenderData(state, gameState)

            for (coord in arc) {
                assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[coord])
            }
        }

        @Test
        fun `attack with empty arc produces empty highlights`() {
            // Single-hex map: attacker is the only hex; arc is empty (forwardArc excludes origin)
            val attacker = aUnit(
                id = "u1", owner = PlayerId.PLAYER_1,
                position = HexCoordinates(0, 0), facing = HexDirection.N,
                weapons = listOf(mediumLaser()),
            )
            val gameState = aGameState(
                units = listOf(attacker),
                map = GameMap(mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0), Terrain.CLEAR))),
            )
            assertTrue(fireArc(attacker, HexDirection.N, gameState).isEmpty())

            val state = attackState(attacker.id, torsoFacing = HexDirection.N)
            val result = extractRenderData(state, gameState)

            assertEquals(emptyMap<HexCoordinates, HexHighlight>(), result.hexHighlights)
        }

        @Test
        fun `attack with valid target produces LINE_OF_SIGHT on intervening hexes`() {
            val attacker = aUnit(
                id = "attacker", position = HexCoordinates(0, 0), owner = PlayerId.PLAYER_1,
                facing = HexDirection.S, weapons = listOf(mediumLaser()),
            )
            val target = aUnit(id = "target", position = HexCoordinates(0, 3), owner = PlayerId.PLAYER_2)
            val hexes = (0..3).associate { row ->
                HexCoordinates(0, row) to Hex(HexCoordinates(0, row), Terrain.CLEAR)
            }
            val gameState = aGameState(units = listOf(attacker, target), map = GameMap(hexes))
            val state = attackState(attacker.id, torsoFacing = HexDirection.S, cursorTargetIndex = -1)

            val result = extractRenderData(state, gameState)

            // Intervening hexes get LINE_OF_SIGHT (white)
            assertEquals(HexHighlight.LINE_OF_SIGHT, result.hexHighlights[HexCoordinates(0, 1)])
            assertEquals(HexHighlight.LINE_OF_SIGHT, result.hexHighlights[HexCoordinates(0, 2)])
            // Target hex stays ATTACK_RANGE (gray)
            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(0, 3)])
        }

        @Test
        fun `attack with target in heavy woods produces no LINE_OF_SIGHT`() {
            val attacker = aUnit(
                id = "attacker", position = HexCoordinates(0, 0), owner = PlayerId.PLAYER_1,
                facing = HexDirection.S, weapons = listOf(mediumLaser()),
            )
            val target = aUnit(id = "target", position = HexCoordinates(0, 3), owner = PlayerId.PLAYER_2)
            val hexes = (0..3).associate { row ->
                HexCoordinates(0, row) to Hex(HexCoordinates(0, row), if (row == 3) Terrain.HEAVY_WOODS else Terrain.CLEAR)
            }
            val gameState = aGameState(units = listOf(attacker, target), map = GameMap(hexes))
            val state = attackState(attacker.id, torsoFacing = HexDirection.S, cursorTargetIndex = -1)

            val result = extractRenderData(state, gameState)

            // No LoS — all arc hexes remain ATTACK_RANGE
            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(0, 1)])
            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(0, 2)])
            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(0, 3)])
        }

        @Test
        fun `attack with adjacent target produces no LINE_OF_SIGHT intervening hexes`() {
            val attacker = aUnit(
                id = "attacker", position = HexCoordinates(0, 0), owner = PlayerId.PLAYER_1,
                facing = HexDirection.S, weapons = listOf(mediumLaser()),
            )
            val target = aUnit(id = "target", position = HexCoordinates(0, 1), owner = PlayerId.PLAYER_2)
            val hexes = mapOf(
                HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0), Terrain.CLEAR),
                HexCoordinates(0, 1) to Hex(HexCoordinates(0, 1), Terrain.CLEAR),
            )
            val gameState = aGameState(units = listOf(attacker, target), map = GameMap(hexes))
            val state = attackState(attacker.id, torsoFacing = HexDirection.S, cursorTargetIndex = -1)

            val result = extractRenderData(state, gameState)

            // No intervening hexes — target hex stays ATTACK_RANGE
            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(0, 1)])
        }

        @Test
        fun `attack with selected target produces LINE_OF_SIGHT_SELECTED on intervening hexes`() {
            val attacker = aUnit(
                id = "attacker", position = HexCoordinates(0, 0), owner = PlayerId.PLAYER_1,
                facing = HexDirection.S, weapons = listOf(mediumLaser()),
            )
            val target = aUnit(id = "target", position = HexCoordinates(0, 3), owner = PlayerId.PLAYER_2)
            val hexes = (0..3).associate { row ->
                HexCoordinates(0, row) to Hex(HexCoordinates(0, row), Terrain.CLEAR)
            }
            val gameState = aGameState(units = listOf(attacker, target), map = GameMap(hexes))
            val state = attackState(attacker.id, torsoFacing = HexDirection.S, cursorTargetIndex = 0)

            val result = extractRenderData(state, gameState)

            assertEquals(HexHighlight.LINE_OF_SIGHT_SELECTED, result.hexHighlights[HexCoordinates(0, 1)])
            assertEquals(HexHighlight.LINE_OF_SIGHT_SELECTED, result.hexHighlights[HexCoordinates(0, 2)])
        }

        @Test
        fun `attack with multiple targets only highlights selected target's line in yellow`() {
            val attacker = aUnit(
                id = "attacker", position = HexCoordinates(0, 0), owner = PlayerId.PLAYER_1,
                facing = HexDirection.S, weapons = listOf(mediumLaser()),
            )
            val selected = aUnit(id = "selected", position = HexCoordinates(0, 3), owner = PlayerId.PLAYER_2)
            val otherFar = aUnit(id = "otherFar", position = HexCoordinates(2, 1), owner = PlayerId.PLAYER_2)
            val hexes = listOf(
                HexCoordinates(0, 0), HexCoordinates(0, 1), HexCoordinates(0, 2), HexCoordinates(0, 3),
                HexCoordinates(1, 0), HexCoordinates(2, 1),
            ).associateWith { Hex(it, Terrain.CLEAR) }
            val gameState = aGameState(units = listOf(attacker, selected, otherFar), map = GameMap(hexes))

            val targets = targetInfos(attacker, HexDirection.S, gameState)
            val selectedIdx = targets.indexOfFirst { it.unitId == selected.id }
            assertTrue(selectedIdx >= 0)

            val state = attackState(attacker.id, torsoFacing = HexDirection.S, cursorTargetIndex = selectedIdx)
            val result = extractRenderData(state, gameState)

            // Selected's line: (0,1), (0,2) → YELLOW
            assertEquals(HexHighlight.LINE_OF_SIGHT_SELECTED, result.hexHighlights[HexCoordinates(0, 1)])
            assertEquals(HexHighlight.LINE_OF_SIGHT_SELECTED, result.hexHighlights[HexCoordinates(0, 2)])
            // otherFar's line: (1,0) → WHITE (intervening between (0,0) and (2,1))
            assertEquals(HexHighlight.LINE_OF_SIGHT, result.hexHighlights[HexCoordinates(1, 0)])
        }

        @Test
        fun `attack with selected target in heavy woods produces no yellow line`() {
            val attacker = aUnit(
                id = "attacker", position = HexCoordinates(0, 0), owner = PlayerId.PLAYER_1,
                facing = HexDirection.S, weapons = listOf(mediumLaser()),
            )
            val target = aUnit(id = "target", position = HexCoordinates(0, 3), owner = PlayerId.PLAYER_2)
            val hexes = (0..3).associate { row ->
                HexCoordinates(0, row) to Hex(HexCoordinates(0, row), if (row == 3) Terrain.HEAVY_WOODS else Terrain.CLEAR)
            }
            val gameState = aGameState(units = listOf(attacker, target), map = GameMap(hexes))
            val state = attackState(attacker.id, torsoFacing = HexDirection.S, cursorTargetIndex = 0)

            val result = extractRenderData(state, gameState)

            // No yellow — heavy woods blocks LoS
            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(0, 1)])
            assertEquals(HexHighlight.ATTACK_RANGE, result.hexHighlights[HexCoordinates(0, 2)])
        }

        @Test
        fun `attack with cursor on no target produces no LINE_OF_SIGHT_SELECTED`() {
            val attacker = aUnit(
                id = "attacker", position = HexCoordinates(0, 0), owner = PlayerId.PLAYER_1,
                facing = HexDirection.S, weapons = listOf(mediumLaser()),
            )
            val target = aUnit(id = "target", position = HexCoordinates(0, 3), owner = PlayerId.PLAYER_2)
            val hexes = (0..3).associate { row ->
                HexCoordinates(0, row) to Hex(HexCoordinates(0, row), Terrain.CLEAR)
            }
            val gameState = aGameState(units = listOf(attacker, target), map = GameMap(hexes))
            // cursorTargetIndex = -1 → no target selected
            val state = attackState(attacker.id, torsoFacing = HexDirection.S, cursorTargetIndex = -1)

            val result = extractRenderData(state, gameState)

            // No selected — white LoS drawn, but no yellow
            assertEquals(HexHighlight.LINE_OF_SIGHT, result.hexHighlights[HexCoordinates(0, 1)])
            assertEquals(HexHighlight.LINE_OF_SIGHT, result.hexHighlights[HexCoordinates(0, 2)])
        }
    }
}
