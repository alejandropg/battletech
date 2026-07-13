package battletech.tactical.model.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

internal class MapSourceTest {

    @Test
    fun `built-in id resolves to the catalog map`() {
        assertThat(resolveMap("default")).isEqualTo(MapCatalog.defaultMap())
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
