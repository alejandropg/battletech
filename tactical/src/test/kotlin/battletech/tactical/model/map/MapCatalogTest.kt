package battletech.tactical.model.map

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MapCatalogTest {

    @Test
    fun `ids contains default`() {
        assertThat(MapCatalog.ids()).contains("default")
    }

    @Test
    fun `get resolves the default map by id`() {
        assertThat(MapCatalog["default"]).isEqualTo(MapCatalog.defaultMap())
    }

    @Test
    fun `get returns null for an unknown id`() {
        assertThat(MapCatalog["nope"]).isNull()
    }

    @Test
    fun `defaultMap is a ten by ten board`() {
        assertThat(MapCatalog.defaultMap().hexes).hasSize(100)
    }

    @Test
    fun `defaultMap reproduces the original terrain and elevation rules`() {
        val map = MapCatalog.defaultMap()

        // Light woods family: col 3, row 2..5
        assertThat(map.hexes.getValue(HexCoordinates(3, 2)).terrain).isEqualTo(Terrain.LIGHT_WOODS)
        assertThat(map.hexes.getValue(HexCoordinates(3, 5)).terrain).isEqualTo(Terrain.LIGHT_WOODS)
        assertThat(map.hexes.getValue(HexCoordinates(3, 6)).terrain).isEqualTo(Terrain.CLEAR)

        // Heavy woods family: col 4, row 3..4
        assertThat(map.hexes.getValue(HexCoordinates(4, 3)).terrain).isEqualTo(Terrain.HEAVY_WOODS)
        assertThat(map.hexes.getValue(HexCoordinates(4, 4)).terrain).isEqualTo(Terrain.HEAVY_WOODS)
        assertThat(map.hexes.getValue(HexCoordinates(4, 5)).terrain).isEqualTo(Terrain.CLEAR)

        // Water family: col 6, row 1..3
        assertThat(map.hexes.getValue(HexCoordinates(6, 1)).terrain).isEqualTo(Terrain.WATER)
        assertThat(map.hexes.getValue(HexCoordinates(6, 3)).terrain).isEqualTo(Terrain.WATER)
        assertThat(map.hexes.getValue(HexCoordinates(6, 4)).terrain).isEqualTo(Terrain.CLEAR)

        // Elevation family: col 5 — row 2 is a level-2 hill, rows 3..4 are level 1
        assertThat(map.hexes.getValue(HexCoordinates(5, 2)).elevation).isEqualTo(2)
        assertThat(map.hexes.getValue(HexCoordinates(5, 4)).elevation).isEqualTo(1)
        assertThat(map.hexes.getValue(HexCoordinates(5, 5)).elevation).isEqualTo(0)

        // A plain clear hex outside every special family
        val clear = map.hexes.getValue(HexCoordinates(0, 0))
        assertThat(clear.terrain).isEqualTo(Terrain.CLEAR)
        assertThat(clear.elevation).isEqualTo(0)
        assertThat(clear.depth).isEqualTo(0)
    }
}
