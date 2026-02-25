package battletech.tactical.movement

import battletech.tactical.action.aUnit
import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.Terrain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

internal class ReachabilityCalculatorTest {

    private fun flatClearMap(radius: Int): GameMap {
        val hexes = mutableMapOf<HexCoordinates, Hex>()
        for (col in -radius..radius) {
            for (row in -radius..radius) {
                val coords = HexCoordinates(col, row)
                hexes[coords] = Hex(coords)
            }
        }
        return GameMap(hexes)
    }

    private fun calculator(map: GameMap, units: List<battletech.tactical.action.Unit>) =
        ReachabilityCalculator(map, units)

    // --- Walk/Run Tests ---

    @Test
    fun `1 MP walk - forward neighbor or turn in place`() {
        val map = flatClearMap(3)
        val actor = aUnit(position = HexCoordinates(0, 0), walkingMP = 1)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        val positions = result.destinations.map { it.position }.toSet()
        assertThat(positions).containsExactlyInAnyOrder(
            HexCoordinates(0, -1), // forward
            HexCoordinates(0, 0),  // turn in place
        )
        // Only adjacent facings reachable with 1 MP
        val originFacings = result.destinations
            .filter { it.position == HexCoordinates(0, 0) }
            .map { it.facing }.toSet()
        assertThat(originFacings).containsExactlyInAnyOrder(HexDirection.NE, HexDirection.NW)
    }

    @Test
    fun `2 MP walk - can turn then move`() {
        val map = flatClearMap(3)
        val actor = aUnit(position = HexCoordinates(0, 0), walkingMP = 2)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        val positions = result.destinations.map { it.position }.toSet()
        // 1 MP turn + 1 MP move forward in new direction
        // Can reach: N (1 MP), NE (turn CW + move = 2), NW (turn CCW + move = 2)
        assertThat(positions).contains(
            HexCoordinates(0, -1), // N, 1 MP forward
        )
        // After turning NE and moving: should reach NE neighbor
        assertThat(positions).contains(HexCoordinates(1, -1)) // NE neighbor from even col (0,0)
        // After turning NW and moving: should reach NW neighbor
        assertThat(positions).contains(HexCoordinates(-1, -1)) // NW neighbor from even col (0,0)
    }

    @Test
    fun `terrain costs reduce reachable area`() {
        val origin = HexCoordinates(0, 0)
        val northHex = HexCoordinates(0, -1)
        val hexes = mapOf(
            origin to Hex(origin, Terrain.CLEAR),
            northHex to Hex(northHex, Terrain.HEAVY_WOODS),
        )
        val map = GameMap(hexes)
        val actor = aUnit(position = origin, walkingMP = 2)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        // Heavy woods costs 3 MP, actor only has 2 — cannot reach
        val positions = result.destinations.map { it.position }.toSet()
        assertThat(positions).doesNotContain(northHex)
    }

    @Test
    fun `elevation climb adds to cost`() {
        val origin = HexCoordinates(0, 0)
        val northHex = HexCoordinates(0, -1)
        val hexes = mapOf(
            origin to Hex(origin, Terrain.CLEAR, elevation = 0),
            northHex to Hex(northHex, Terrain.CLEAR, elevation = 2),
        )
        val map = GameMap(hexes)
        val actor = aUnit(position = origin, walkingMP = 2)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        // Clear (1) + climb 2 levels (2) = 3 MP, actor has 2 — cannot reach
        val positions = result.destinations.map { it.position }.toSet()
        assertThat(positions).doesNotContain(northHex)
    }

    @Test
    fun `descent is free - only terrain cost applies`() {
        val origin = HexCoordinates(0, 0)
        val northHex = HexCoordinates(0, -1)
        val hexes = mapOf(
            origin to Hex(origin, Terrain.CLEAR, elevation = 3),
            northHex to Hex(northHex, Terrain.CLEAR, elevation = 0),
        )
        val map = GameMap(hexes)
        val actor = aUnit(position = origin, walkingMP = 1)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        val positions = result.destinations.map { it.position }.toSet()
        assertThat(positions).contains(northHex)
    }

    @Test
    fun `enemy hex blocks movement`() {
        val map = flatClearMap(3)
        val origin = HexCoordinates(0, 0)
        val enemyPos = HexCoordinates(0, -1)
        val actor = aUnit(id = "actor", position = origin, walkingMP = 4)
        val enemy = aUnit(id = "enemy", position = enemyPos)
        val calc = calculator(map, listOf(actor, enemy))

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        val positions = result.destinations.map { it.position }.toSet()
        assertThat(positions).doesNotContain(enemyPos)
    }

    @Test
    fun `enemy hex is not traversable`() {
        // Enemy at (0,-1) blocks the path north; hex (0,-2) unreachable going straight north
        val origin = HexCoordinates(0, 0)
        val enemyPos = HexCoordinates(0, -1)
        val beyondEnemy = HexCoordinates(0, -2)
        val hexes = mapOf(
            origin to Hex(origin),
            enemyPos to Hex(enemyPos),
            beyondEnemy to Hex(beyondEnemy),
        )
        val map = GameMap(hexes)
        val actor = aUnit(id = "actor", position = origin, walkingMP = 2)
        val enemy = aUnit(id = "enemy", position = enemyPos)
        val calc = calculator(map, listOf(actor, enemy))

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        val positions = result.destinations.map { it.position }.toSet()
        assertThat(positions).doesNotContain(beyondEnemy)
    }

    @Test
    fun `0 MP produces no destinations`() {
        val map = flatClearMap(3)
        val actor = aUnit(position = HexCoordinates(0, 0), walkingMP = 0)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        assertThat(result.destinations).isEmpty()
    }

    @Test
    fun `start position is reachable with different facing`() {
        val map = flatClearMap(3)
        val actor = aUnit(position = HexCoordinates(0, 0), walkingMP = 4)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        val atOrigin = result.destinations.filter { it.position == HexCoordinates(0, 0) }
        assertThat(atOrigin).isNotEmpty
        assertThat(atOrigin.map { it.facing }).doesNotContain(HexDirection.N)
    }

    @Test
    fun `start position with same facing is not a destination`() {
        val map = flatClearMap(3)
        val actor = aUnit(position = HexCoordinates(0, 0), walkingMP = 4)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        val sameState = result.destinations.find {
            it.position == HexCoordinates(0, 0) && it.facing == HexDirection.N
        }
        assertThat(sameState).isNull()
    }

    @Test
    fun `path contains intermediate steps for multi-hex move`() {
        val map = flatClearMap(3)
        val actor = aUnit(position = HexCoordinates(0, 0), walkingMP = 2)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        // Moving 2 hexes north: (0,0) → (0,-1) → (0,-2)
        val twoNorth = result.destinations.find {
            it.position == HexCoordinates(0, -2) && it.facing == HexDirection.N
        }
        assertNotNull(twoNorth)
        assertThat(twoNorth.path).hasSize(2)
        assertEquals(MovementStep(HexCoordinates(0, -1), HexDirection.N), twoNorth.path[0])
        assertEquals(MovementStep(HexCoordinates(0, -2), HexDirection.N), twoNorth.path[1])
    }

    @Test
    fun `turn recorded in path`() {
        val map = flatClearMap(3)
        val actor = aUnit(position = HexCoordinates(0, 0), walkingMP = 2)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        // Turn NE (1 MP) then move NE (1 MP)
        val neNeighbor = HexCoordinates(0, 0).neighbor(HexDirection.NE)
        val turnAndMove = result.destinations.find {
            it.position == neNeighbor && it.facing == HexDirection.NE
        }
        assertNotNull(turnAndMove)
        assertThat(turnAndMove.path).hasSize(2)
        // First step: turn at origin
        assertEquals(MovementStep(HexCoordinates(0, 0), HexDirection.NE), turnAndMove.path[0])
        // Second step: move to NE neighbor
        assertEquals(MovementStep(neNeighbor, HexDirection.NE), turnAndMove.path[1])
    }

    @Test
    fun `run reaches more positions than walk`() {
        val map = flatClearMap(5)
        val actor = aUnit(position = HexCoordinates(0, 0), walkingMP = 2, runningMP = 4)
        val calc = calculator(map, listOf(actor))

        val walkResult = calc.calculate(actor, MovementMode.WALK, HexDirection.N)
        val runResult = calc.calculate(actor, MovementMode.RUN, HexDirection.N)

        val walkPositions = walkResult.destinations.map { it.position }.toSet()
        val runPositions = runResult.destinations.map { it.position }.toSet()
        assertThat(runPositions).containsAll(walkPositions)
        assertThat(runPositions.size).isGreaterThan(walkPositions.size)
    }

    @Test
    fun `surrounded by enemies can only change facing in place`() {
        val origin = HexCoordinates(0, 0)
        val hexes = mutableMapOf(origin to Hex(origin))
        val enemies = origin.neighbors().mapIndexed { index, pos ->
            hexes[pos] = Hex(pos)
            aUnit(id = "enemy-$index", position = pos)
        }
        val map = GameMap(hexes)
        val actor = aUnit(id = "actor", position = origin, walkingMP = 4)
        val calc = calculator(map, listOf(actor) + enemies)

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        val positions = result.destinations.map { it.position }.toSet()
        assertThat(positions).containsExactly(origin)
        assertThat(result.destinations.map { it.facing }).doesNotContain(HexDirection.N)
    }

    // --- Jump Tests ---

    @Test
    fun `jump reaches all hexes within JP distance`() {
        val map = flatClearMap(5)
        val origin = HexCoordinates(0, 0)
        val actor = aUnit(position = origin, jumpMP = 2)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.JUMP, HexDirection.N)

        val positions = result.destinations.map { it.position }.toSet()
        val expectedPositions = map.hexes.keys
            .filter { it != origin }
            .filter { origin.distanceTo(it) in 1..2 }
            .toSet()
        assertEquals(expectedPositions, positions)
    }

    @Test
    fun `jump provides any facing at destination - 6 entries per hex`() {
        val map = flatClearMap(3)
        val origin = HexCoordinates(0, 0)
        val actor = aUnit(position = origin, jumpMP = 1)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.JUMP, HexDirection.N)

        val northHex = HexCoordinates(0, -1)
        val facingsAtNorth = result.destinations
            .filter { it.position == northHex }
            .map { it.facing }
            .toSet()
        assertEquals(HexDirection.entries.toSet(), facingsAtNorth)
    }

    @Test
    fun `jump ignores terrain`() {
        val origin = HexCoordinates(0, 0)
        val target = HexCoordinates(0, -1)
        val hexes = mapOf(
            origin to Hex(origin, Terrain.CLEAR),
            target to Hex(target, Terrain.HEAVY_WOODS),
        )
        val map = GameMap(hexes)
        val actor = aUnit(position = origin, jumpMP = 1)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.JUMP, HexDirection.N)

        val positions = result.destinations.map { it.position }.toSet()
        assertThat(positions).contains(target)
    }

    @Test
    fun `jump cannot land on any occupied hex`() {
        val map = flatClearMap(3)
        val origin = HexCoordinates(0, 0)
        val occupiedPos = HexCoordinates(0, -1)
        val actor = aUnit(id = "actor", position = origin, jumpMP = 2)
        val other = aUnit(id = "other", position = occupiedPos)
        val calc = calculator(map, listOf(actor, other))

        val result = calc.calculate(actor, MovementMode.JUMP, HexDirection.N)

        val positions = result.destinations.map { it.position }.toSet()
        assertThat(positions).doesNotContain(occupiedPos)
    }

    @Test
    fun `0 JP produces no destinations`() {
        val map = flatClearMap(3)
        val actor = aUnit(position = HexCoordinates(0, 0), jumpMP = 0)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.JUMP, HexDirection.N)

        assertThat(result.destinations).isEmpty()
    }

    @Test
    fun `jump cost equals hex distance`() {
        val map = flatClearMap(5)
        val origin = HexCoordinates(0, 0)
        val actor = aUnit(position = origin, jumpMP = 3)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.JUMP, HexDirection.N)

        result.destinations.forEach { dest ->
            assertEquals(origin.distanceTo(dest.position), dest.mpSpent)
        }
    }

    // --- Metadata Tests ---

    @Test
    fun `result contains correct mode`() {
        val map = flatClearMap(3)
        val actor = aUnit(position = HexCoordinates(0, 0), walkingMP = 2)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.WALK, HexDirection.N)

        assertEquals(MovementMode.WALK, result.mode)
    }

    @Test
    fun `result contains correct maxMP`() {
        val map = flatClearMap(3)
        val actor = aUnit(position = HexCoordinates(0, 0), runningMP = 6)
        val calc = calculator(map, listOf(actor))

        val result = calc.calculate(actor, MovementMode.RUN, HexDirection.N)

        assertEquals(6, result.maxMP)
    }
}
