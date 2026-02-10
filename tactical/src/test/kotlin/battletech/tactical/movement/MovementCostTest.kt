package battletech.tactical.movement

import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MovementCostTest {

    @Test
    fun `clear terrain costs 1`() {
        assertThat(MovementCost.terrainCost(Terrain.CLEAR)).isEqualTo(1)
    }

    @Test
    fun `light woods costs 2`() {
        assertThat(MovementCost.terrainCost(Terrain.LIGHT_WOODS)).isEqualTo(2)
    }

    @Test
    fun `heavy woods costs 3`() {
        assertThat(MovementCost.terrainCost(Terrain.HEAVY_WOODS)).isEqualTo(3)
    }

    @Test
    fun `water costs 2`() {
        assertThat(MovementCost.terrainCost(Terrain.WATER)).isEqualTo(2)
    }

    @Test
    fun `entering hex at same elevation costs only terrain`() {
        val from = Hex(HexCoordinates(0, 0), Terrain.CLEAR, elevation = 1)
        val to = Hex(HexCoordinates(1, 0), Terrain.LIGHT_WOODS, elevation = 1)

        assertThat(MovementCost.enterHexCost(from, to)).isEqualTo(2)
    }

    @Test
    fun `climbing one level adds 1 to terrain cost`() {
        val from = Hex(HexCoordinates(0, 0), Terrain.CLEAR, elevation = 0)
        val to = Hex(HexCoordinates(1, 0), Terrain.CLEAR, elevation = 1)

        assertThat(MovementCost.enterHexCost(from, to)).isEqualTo(2)
    }

    @Test
    fun `climbing two levels adds 2 to terrain cost`() {
        val from = Hex(HexCoordinates(0, 0), Terrain.CLEAR, elevation = 0)
        val to = Hex(HexCoordinates(1, 0), Terrain.LIGHT_WOODS, elevation = 2)

        assertThat(MovementCost.enterHexCost(from, to)).isEqualTo(4)
    }

    @Test
    fun `descending is free - only terrain cost applies`() {
        val from = Hex(HexCoordinates(0, 0), Terrain.CLEAR, elevation = 3)
        val to = Hex(HexCoordinates(1, 0), Terrain.CLEAR, elevation = 0)

        assertThat(MovementCost.enterHexCost(from, to)).isEqualTo(1)
    }
}
