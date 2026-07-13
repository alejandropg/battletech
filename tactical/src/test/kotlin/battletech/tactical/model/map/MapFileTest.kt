package battletech.tactical.model.map

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class MapFileTest {

    @Test
    fun `unlisted hexes are generated as clear`() {
        val map = MapFile(width = 2, height = 2).toGameMap()

        assertThat(map.hexes).hasSize(4)
        map.hexes.values.forEach { hex ->
            assertThat(hex.terrain).isEqualTo(Terrain.CLEAR)
            assertThat(hex.elevation).isEqualTo(0)
            assertThat(hex.depth).isEqualTo(0)
        }
    }

    @Test
    fun `listed hexes overlay their terrain elevation and depth`() {
        val mapFile = MapFile(
            width = 3,
            height = 3,
            hexes = listOf(
                HexSpec(col = 1, row = 1, terrain = Terrain.WATER, elevation = 0, depth = 2),
                HexSpec(col = 2, row = 0, terrain = Terrain.HEAVY_WOODS, elevation = 1),
            ),
        )

        val map = mapFile.toGameMap()

        val special = map.hexes.getValue(HexCoordinates(1, 1))
        assertThat(special.terrain).isEqualTo(Terrain.WATER)
        assertThat(special.depth).isEqualTo(2)

        val woods = map.hexes.getValue(HexCoordinates(2, 0))
        assertThat(woods.terrain).isEqualTo(Terrain.HEAVY_WOODS)
        assertThat(woods.elevation).isEqualTo(1)

        val untouched = map.hexes.getValue(HexCoordinates(0, 0))
        assertThat(untouched.terrain).isEqualTo(Terrain.CLEAR)
    }

    @Test
    fun `out of bounds column throws MapLoadException`() {
        val mapFile = MapFile(width = 2, height = 2, hexes = listOf(HexSpec(col = 5, row = 0)))

        assertThrows<MapLoadException> { mapFile.toGameMap() }
    }

    @Test
    fun `out of bounds row throws MapLoadException`() {
        val mapFile = MapFile(width = 2, height = 2, hexes = listOf(HexSpec(col = 0, row = 5)))

        assertThrows<MapLoadException> { mapFile.toGameMap() }
    }

    @Test
    fun `non-positive width throws MapLoadException`() {
        assertThrows<MapLoadException> { MapFile(width = 0, height = 2).toGameMap() }
    }

    @Test
    fun `non-positive height throws MapLoadException`() {
        assertThrows<MapLoadException> { MapFile(width = 2, height = -1).toGameMap() }
    }
}
