package battletech.tactical.model.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

internal class MapSourceTest {

    @Test
    fun `built-in id resolves to the catalog map`() {
        assertThat(resolveMap("default")).isEqualTo(MapCatalog.defaultMap())
    }

    /**
     * [resolveMap]`("default")` returns [MapCatalog.defaultMap] directly and never reads
     * `maps/default.json` — a built-in id always wins over the file-loader fallback (see
     * [resolveMap]'s KDoc). That means the test above proves nothing about the file: the two
     * representations could silently drift and no other test would catch it. This one loads
     * `maps/default.json` explicitly and compares every cell against [MapCatalog.defaultMap],
     * cell by cell rather than via `GameMap` equality, so a mismatch names the coordinate and the
     * field, not just "not equal".
     */
    @Test
    fun `maps default json matches MapCatalog defaultMap`() {
        val file = repoRoot().resolve("maps/default.json")
        val fromFile = GameMapLoader().load(file)
        val fromCatalog = MapCatalog.defaultMap()

        assertThat(fromFile.hexes.keys).isEqualTo(fromCatalog.hexes.keys)
        for (coords in fromCatalog.hexes.keys) {
            val expected = fromCatalog.hexes.getValue(coords)
            val actual = fromFile.hexes.getValue(coords)
            assertThat(actual.terrain).describedAs("terrain at $coords").isEqualTo(expected.terrain)
            assertThat(actual.elevation).describedAs("elevation at $coords").isEqualTo(expected.elevation)
            assertThat(actual.depth).describedAs("depth at $coords").isEqualTo(expected.depth)
        }
    }

    /** Walks upward from the working directory to the directory containing `settings.gradle.kts`, so this test resolves `maps/` under both Gradle's and an IDE's working directory. */
    private fun repoRoot(): Path {
        var dir = Path.of("").toAbsolutePath()
        while (!dir.resolve("settings.gradle.kts").exists()) {
            dir = dir.parent ?: error("Could not locate settings.gradle.kts above ${Path.of("").toAbsolutePath()}")
        }
        return dir
    }

    @Test
    fun `unknown spec resolves via the file loader`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("map.json")
        file.writeText("""{"width":1,"height":1,"hexes":[]}""")

        val map = resolveMap(file.toString())

        assertThat(map).isEqualTo(GameMapLoader().load(file))
    }

    @Test
    fun `unknown id that is not a real file throws MapLoadException`() {
        assertThrows<MapLoadException> { resolveMap("not-a-builtin-and-not-a-file.json") }
    }
}
