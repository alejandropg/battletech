package battletech.tactical.model.unit

import battletech.tactical.io.NestedCatalogEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal class UnitCatalogTest {

    @field:TempDir
    private lateinit var tempDir: Path

    @Test
    fun `reject external unit collection whose name collides with a built-in`() {
        val path = writeUnits(tempDir.resolve("default.json"))

        val exception = assertThrows<UnitLoadException> { UnitCatalog.load(listOf(path)) }

        assertThat(exception.message).contains("conflicts with built-in unit collection 'default'")
    }

    @Test
    fun `reject duplicate external names from different directories`() {
        val first = writeUnits(tempDir.resolve("one").createDirectories().resolve("lance.json"))
        val second = writeUnits(tempDir.resolve("two").createDirectories().resolve("lance.json"))

        val exception = assertThrows<UnitLoadException> { UnitCatalog.load(listOf(first, second)) }

        assertThat(exception.message).contains("'lance' is registered more than once")
    }

    @Test
    fun `reject malformed external unit collection while building catalog`() {
        val path = tempDir.resolve("broken.json")
        path.writeText("{not json")

        assertThrows<UnitLoadException> { UnitCatalog.load(listOf(path)) }
    }

    @Test
    fun `unknown name lists built-in and external catalog entries`() {
        val path = writeUnits(tempDir.resolve("lance.json"))
        val catalog = UnitCatalog.load(listOf(path))

        assertThat(catalog.entries().map { it.name }).contains("lance", "default")
    }

    @Test
    fun `entries lists built-ins then externals, each with its unit ids`() {
        val path = writeUnits(tempDir.resolve("lance.json"))
        val catalog = UnitCatalog.load(listOf(path))

        val entries = catalog.entries()

        assertThat(entries).contains(NestedCatalogEntry("default", external = false, items = listOf("A1", "H1", "W1", "W2")))
        val external = entries.first { it.name == "lance" }
        assertThat(external.external).isTrue()
        assertThat(external.items).containsExactly("X1")
        assertThat(entries.indexOf(entries.first { it.name == "default" })).isLessThan(entries.indexOf(external))
    }

    private fun writeUnits(path: Path): Path {
        path.writeText(
            """
            {"units":[{"id":"X1","player":1,"variant":"LCT-1V","gunnerySkill":4,"pilotingSkill":5,
              "position":{"col":1,"row":1},"facing":"N"}]}
            """.trimIndent(),
        )
        return path
    }
}
