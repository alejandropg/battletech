package battletech.tactical.model.map

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

internal class MapSourceTest {

    @Test
    fun `packaged default name resolves the expected terrain families`() {
        val map = resolveMap("default")

        assertThat(map.hexes).hasSize(100)

        assertThat(map.hexes.getValue(HexCoordinates(3, 2)).terrain).isEqualTo(Terrain.LIGHT_WOODS)
        assertThat(map.hexes.getValue(HexCoordinates(3, 5)).terrain).isEqualTo(Terrain.LIGHT_WOODS)
        assertThat(map.hexes.getValue(HexCoordinates(3, 6)).terrain).isEqualTo(Terrain.CLEAR)

        assertThat(map.hexes.getValue(HexCoordinates(4, 3)).terrain).isEqualTo(Terrain.HEAVY_WOODS)
        assertThat(map.hexes.getValue(HexCoordinates(4, 4)).terrain).isEqualTo(Terrain.HEAVY_WOODS)
        assertThat(map.hexes.getValue(HexCoordinates(4, 5)).terrain).isEqualTo(Terrain.CLEAR)

        assertThat(map.hexes.getValue(HexCoordinates(6, 1)).terrain).isEqualTo(Terrain.WATER)
        assertThat(map.hexes.getValue(HexCoordinates(6, 3)).terrain).isEqualTo(Terrain.WATER)
        assertThat(map.hexes.getValue(HexCoordinates(6, 4)).terrain).isEqualTo(Terrain.CLEAR)

        val clear = map.hexes.getValue(HexCoordinates(0, 0))
        assertThat(clear.terrain).isEqualTo(Terrain.CLEAR)
        assertThat(clear.elevation).isEqualTo(0)
        assertThat(clear.depth).isEqualTo(0)
    }

    @Test
    fun `packaged default map preserves elevation depth and rough terrain`() {
        val map = resolveMap("default")

        assertThat(map.hexes.getValue(HexCoordinates(5, 1)).elevation).isEqualTo(3)
        assertThat(map.hexes.getValue(HexCoordinates(5, 2)).elevation).isEqualTo(2)
        assertThat(map.hexes.getValue(HexCoordinates(5, 3)).elevation).isEqualTo(1)
        assertThat(map.hexes.getValue(HexCoordinates(5, 4)).elevation).isEqualTo(1)
        assertThat(map.hexes.getValue(HexCoordinates(5, 5)).elevation).isEqualTo(0)

        assertThat(map.hexes.getValue(HexCoordinates(3, 2)).elevation).isEqualTo(1)
        assertThat(map.hexes.getValue(HexCoordinates(3, 3)).elevation).isEqualTo(0)
        assertThat(map.hexes.getValue(HexCoordinates(4, 3)).elevation).isEqualTo(2)
        assertThat(map.hexes.getValue(HexCoordinates(4, 4)).elevation).isEqualTo(0)

        assertThat(map.hexes.getValue(HexCoordinates(6, 1)).depth).isEqualTo(1)
        assertThat(map.hexes.getValue(HexCoordinates(6, 2)).depth).isEqualTo(2)
        assertThat(map.hexes.getValue(HexCoordinates(6, 3)).depth).isEqualTo(1)

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
    fun `packaged default map keeps sample spawn cells clear at elevation zero`() {
        val map = resolveMap("default")

        for (coords in listOf(HexCoordinates(1, 1), HexCoordinates(2, 3), HexCoordinates(7, 3), HexCoordinates(8, 5))) {
            val hex = map.hexes.getValue(coords)
            assertThat(hex.terrain).describedAs("terrain at $coords").isEqualTo(Terrain.CLEAR)
            assertThat(hex.elevation).describedAs("elevation at $coords").isEqualTo(0)
        }
    }

    @Test
    fun `packaged classic name gains a json suffix automatically`() {
        val map = resolveMap("battletech-classic")

        assertThat(map.hexes).hasSize(255)
        val translated = map.hexes.getValue(HexCoordinates(6, 7))
        assertThat(translated.terrain).isEqualTo(Terrain.WATER)
        assertThat(translated.depth).isEqualTo(2)
    }

    @Test
    fun `existing filesystem path loads its map`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("map.json")
        file.writeText(
            """
            {"width":2,"height":2,"hexes":[{"col":2,"row":1,"terrain":"WATER","depth":1}]}
            """.trimIndent()
        )

        val map = resolveMap(file.toString())

        assertThat(map.hexes).hasSize(4)
        val special = map.hexes.getValue(HexCoordinates(1, 0))
        assertThat(special.terrain).isEqualTo(Terrain.WATER)
        assertThat(special.depth).isEqualTo(1)
    }

    @Test
    fun `malformed existing filesystem path is authoritative`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("default")
        file.writeText("{ not valid json")

        val exception = assertThrows<MapLoadException> { resolveMap(file.toString()) }

        assertThat(exception.message).contains("Malformed map file: $file")
    }

    @Test
    fun `missing packaged name names the built-in maps`() {
        val spec = "not-a-packaged-map-for-test"

        val exception = assertThrows<MapLoadException> { resolveMap(spec) }

        assertThat(exception.message)
            .isEqualTo("Map resource not found: map/$spec.json\nBuilt-in maps: default, battletech-classic")
    }

    @Test
    fun `the built-in index lists exactly the two shipped maps`() {
        assertThat(GameMapLoader().builtInNames()).containsExactlyInAnyOrder("default", "battletech-classic")
    }

    @Test
    fun `every indexed built-in name loads`() {
        for (name in GameMapLoader().builtInNames()) {
            assertThat(resolveMap(name).hexes).describedAs(name).isNotEmpty()
        }
    }

    // ---------- name ----------

    @Test
    fun `a packaged map is named after the spec it was resolved from`() {
        assertThat(resolveMap("default").name).isEqualTo("default")
        assertThat(resolveMap("battletech-classic").name).isEqualTo("battletech-classic")
    }

    @Test
    fun `a filesystem map is named after the path it was loaded from`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("map.json")
        file.writeText("""{"width":2,"height":2,"hexes":[]}""")

        assertThat(resolveMap(file.toString()).name).isEqualTo(file.toString())
    }

    // ---------- compareWithLocalMap ----------

    @Test
    fun `compareWithLocalMap reports MATCHES when the local map of the same name has identical hexes`() {
        val hostMap = resolveMap("default")

        assertThat(compareWithLocalMap(hostMap)).isEqualTo(LocalMapMatch.MATCHES)
    }

    @Test
    fun `compareWithLocalMap reports DIFFERS when the local map of the same name has different hexes`() {
        val hostMap = resolveMap("default").copy(name = "battletech-classic")

        assertThat(compareWithLocalMap(hostMap)).isEqualTo(LocalMapMatch.DIFFERS)
    }

    @Test
    fun `compareWithLocalMap reports UNAVAILABLE when no local map of that name exists`() {
        val hostMap = resolveMap("default").copy(name = "not-a-packaged-map-for-test")

        assertThat(compareWithLocalMap(hostMap)).isEqualTo(LocalMapMatch.UNAVAILABLE)
    }
}
