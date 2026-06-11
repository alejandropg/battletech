package battletech.tactical.movement

import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class MovementCostTest {

    @ParameterizedTest
    @CsvSource(
        "CLEAR, 1",
        "LIGHT_WOODS, 2",
        "HEAVY_WOODS, 3",
        "WATER, 2",
    )
    fun `terrain has its movement cost`(terrain: Terrain, expected: Int) {
        assertEquals(expected, MovementCost.terrainCost(terrain))
    }

    @Test
    fun `entering hex at same elevation costs only terrain`() {
        val from = Hex(HexCoordinates(0, 0), Terrain.CLEAR, elevation = 1)
        val to = Hex(HexCoordinates(1, 0), Terrain.LIGHT_WOODS, elevation = 1)

        assertEquals(2, MovementCost.enterHexCost(from, to))
    }

    @Test
    fun `climbing one level adds 1 to terrain cost`() {
        val from = Hex(HexCoordinates(0, 0), Terrain.CLEAR, elevation = 0)
        val to = Hex(HexCoordinates(1, 0), Terrain.CLEAR, elevation = 1)

        assertEquals(2, MovementCost.enterHexCost(from, to))
    }

    @Test
    fun `climbing two levels adds 2 to terrain cost`() {
        val from = Hex(HexCoordinates(0, 0), Terrain.CLEAR, elevation = 0)
        val to = Hex(HexCoordinates(1, 0), Terrain.LIGHT_WOODS, elevation = 2)

        assertEquals(4, MovementCost.enterHexCost(from, to))
    }

    @Test
    fun `descending is free - only terrain cost applies`() {
        val from = Hex(HexCoordinates(0, 0), Terrain.CLEAR, elevation = 3)
        val to = Hex(HexCoordinates(1, 0), Terrain.CLEAR, elevation = 0)

        assertEquals(1, MovementCost.enterHexCost(from, to))
    }
}
