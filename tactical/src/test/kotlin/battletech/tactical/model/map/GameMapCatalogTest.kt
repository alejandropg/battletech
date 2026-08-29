package battletech.tactical.model.map

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal class GameMapCatalogTest {

    @field:TempDir
    private lateinit var tempDir: Path

    @Test
    fun `resolve packaged map by catalog name`() {
        val catalog = GameMapCatalog.load()

        val map = catalog.resolve("default")

        assertThat(map.name).isEqualTo("default")
        assertThat(map.hexes).hasSize(100)
    }

    @Test
    fun `register external map under filename without json suffix`() {
        val path = writeMap(tempDir.resolve("arena.json"))
        val catalog = GameMapCatalog.load(listOf(path))

        val map = catalog.resolve("arena")

        assertThat(map.name).isEqualTo("arena")
        assertThat(map.hexes.getValue(HexCoordinates(1, 0)).terrain).isEqualTo(Terrain.WATER)
    }

    @Test
    fun `preserve full filename when external map has no json suffix`() {
        val path = writeMap(tempDir.resolve("arena.map"))
        val catalog = GameMapCatalog.load(listOf(path))

        assertThat(catalog.resolve("arena.map").name).isEqualTo("arena.map")
    }

    @Test
    fun `reject external map whose name collides with a built-in`() {
        val path = writeMap(tempDir.resolve("default.json"))

        val exception = assertThrows<MapLoadException> { GameMapCatalog.load(listOf(path)) }

        assertThat(exception.message).contains("conflicts with built-in map 'default'")
    }

    @Test
    fun `reject duplicate external names from different directories`() {
        val first = writeMap(tempDir.resolve("one").createDirectories().resolve("arena.json"))
        val second = writeMap(tempDir.resolve("two").createDirectories().resolve("arena.json"))

        val exception = assertThrows<MapLoadException> { GameMapCatalog.load(listOf(first, second)) }

        assertThat(exception.message).contains("'arena' is registered more than once")
    }

    @Test
    fun `reject malformed external map while building catalog`() {
        val path = tempDir.resolve("broken.json")
        path.writeText("{not json")

        assertThrows<MapLoadException> { GameMapCatalog.load(listOf(path)) }
    }

    @Test
    fun `reject missing external map rather than treating it as a built-in name`() {
        val path = tempDir.resolve("missing.json")

        val exception = assertThrows<MapLoadException> { GameMapCatalog.load(listOf(path)) }

        assertThat(exception.message).contains("Map file not found")
    }

    @Test
    fun `unknown name lists built-in and external catalog entries`() {
        val path = writeMap(tempDir.resolve("arena.json"))
        val catalog = GameMapCatalog.load(listOf(path))

        val exception = assertThrows<MapLoadException> { catalog.resolve("missing") }

        assertThat(exception.message).contains("arena", "battletech-classic", "default")
    }

    private fun writeMap(path: Path): Path {
        path.writeText(
            """
            {"width":2,"height":2,"hexes":[{"col":2,"row":1,"terrain":"WATER","depth":1}]}
            """.trimIndent()
        )
        return path
    }
}
