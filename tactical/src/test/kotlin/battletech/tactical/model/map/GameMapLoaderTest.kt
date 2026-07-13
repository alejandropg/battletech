package battletech.tactical.model.map

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

internal class GameMapLoaderTest {

    private val loader = GameMapLoader()

    @Test
    fun `loads a valid compact map file`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("map.json")
        file.writeText(
            """
            {"width":2,"height":2,"hexes":[{"col":1,"row":0,"terrain":"WATER","depth":1}]}
            """.trimIndent()
        )

        val map = loader.load(file)

        assertThat(map.hexes).hasSize(4)
        val special = map.hexes.getValue(HexCoordinates(1, 0))
        assertThat(special.terrain).isEqualTo(Terrain.WATER)
        assertThat(special.depth).isEqualTo(1)
        val clear = map.hexes.getValue(HexCoordinates(0, 0))
        assertThat(clear.terrain).isEqualTo(Terrain.CLEAR)
    }

    @Test
    fun `missing file throws MapLoadException`(@TempDir tempDir: Path) {
        val missing = tempDir.resolve("does-not-exist.json")

        assertThrows<MapLoadException> { loader.load(missing) }
    }

    @Test
    fun `malformed json throws MapLoadException`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("bad.json")
        file.writeText("{ not valid json")

        assertThrows<MapLoadException> { loader.load(file) }
    }
}
