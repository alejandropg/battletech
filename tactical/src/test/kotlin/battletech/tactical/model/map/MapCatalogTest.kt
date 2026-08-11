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
    fun `defaultMap preserves the original terrain and elevation families`() {
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

    @Test
    fun `defaultMap adds elevation, depth, and rough terrain refinements`() {
        val map = MapCatalog.defaultMap()

        // Clear hilltop family: col 5, rows 1..4 — the level-2 hill at row 2 grows a level-3 peak
        // at row 1, and the level-1 rows extend down to row 4.
        assertThat(map.hexes.getValue(HexCoordinates(5, 1)).elevation).isEqualTo(3)
        assertThat(map.hexes.getValue(HexCoordinates(5, 3)).elevation).isEqualTo(1)

        // Light/heavy woods hexes gain an elevated corner, everything else in the family stays e0.
        assertThat(map.hexes.getValue(HexCoordinates(3, 2)).elevation).isEqualTo(1)
        assertThat(map.hexes.getValue(HexCoordinates(3, 3)).elevation).isEqualTo(0)
        assertThat(map.hexes.getValue(HexCoordinates(4, 3)).elevation).isEqualTo(2)
        assertThat(map.hexes.getValue(HexCoordinates(4, 4)).elevation).isEqualTo(0)

        // Water depth: shallow (<=1) at rows 1 and 3, deep (>=2) at row 2.
        assertThat(map.hexes.getValue(HexCoordinates(6, 1)).depth).isEqualTo(1)
        assertThat(map.hexes.getValue(HexCoordinates(6, 2)).depth).isEqualTo(2)
        assertThat(map.hexes.getValue(HexCoordinates(6, 3)).depth).isEqualTo(1)

        // New rough patch: col 1 rows 6..7, col 2 rows 6..8 — absent from the map before this change.
        assertThat(map.hexes.getValue(HexCoordinates(1, 6)).terrain).isEqualTo(Terrain.ROUGH)
        assertThat(map.hexes.getValue(HexCoordinates(1, 6)).elevation).isEqualTo(0)
        assertThat(map.hexes.getValue(HexCoordinates(1, 7)).terrain).isEqualTo(Terrain.ROUGH)
        assertThat(map.hexes.getValue(HexCoordinates(1, 7)).elevation).isEqualTo(1)
        assertThat(map.hexes.getValue(HexCoordinates(2, 6)).terrain).isEqualTo(Terrain.ROUGH)
        assertThat(map.hexes.getValue(HexCoordinates(2, 6)).elevation).isEqualTo(1)
        assertThat(map.hexes.getValue(HexCoordinates(2, 7)).terrain).isEqualTo(Terrain.ROUGH)
        assertThat(map.hexes.getValue(HexCoordinates(2, 7)).elevation).isEqualTo(2)
        assertThat(map.hexes.getValue(HexCoordinates(2, 8)).terrain).isEqualTo(Terrain.ROUGH)
        assertThat(map.hexes.getValue(HexCoordinates(2, 8)).elevation).isEqualTo(0)
        assertThat(map.hexes.getValue(HexCoordinates(3, 6)).terrain).isEqualTo(Terrain.CLEAR)
    }

    @Test
    fun `defaultMap keeps the sample spawn cells clear at elevation zero`() {
        val map = MapCatalog.defaultMap()

        for (coords in listOf(HexCoordinates(1, 1), HexCoordinates(2, 3), HexCoordinates(7, 3), HexCoordinates(8, 5))) {
            val hex = map.hexes.getValue(coords)
            assertThat(hex.terrain).describedAs("terrain at $coords").isEqualTo(Terrain.CLEAR)
            assertThat(hex.elevation).describedAs("elevation at $coords").isEqualTo(0)
        }
    }
}
