package battletech.tactical.io

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Exercises [ResourceOrFileLoader] generically, independent of any real domain object — [build]
 * here just tags the text with its context [name] so resolution/failure-mode behavior can be
 * asserted directly. `map/` (a real packaged resource directory, already on this module's test
 * classpath via `tactical/build.gradle.kts`'s `processResources`) doubles as the fixture for the
 * classpath-resolution tests; no dedicated test-only resource directory is needed.
 */
internal class ResourceOrFileLoaderTest {

    private class TestLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val json = Json { ignoreUnknownKeys = false }

    private fun loader(resourceDir: String, build: (text: String, name: String) -> String = { text, name -> "$name:$text" }) =
        ResourceOrFileLoader(resourceDir = resourceDir, label = "Thing", json = json, build = build, exception = ::TestLoadException)

    @Test
    fun `resolve loads an existing filesystem path over a packaged resource of the same name`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("test")
        file.writeText("FROM DISK")

        val result = loader("map").resolve(file.toString())

        assertThat(result).isEqualTo("$file:FROM DISK")
    }

    @Test
    fun `resolve loads a packaged resource when spec is not an existing path`() {
        val result = loader("map").resolve("test")

        assertThat(result).startsWith("test:")
    }

    @Test
    fun `a missing file names the label`(@TempDir tempDir: Path) {
        val missing = tempDir.resolve("nope.json")

        val exception = assertThrows<TestLoadException> { loader("map").load(missing) }

        assertThat(exception.message).isEqualTo("Thing file not found: $missing")
    }

    @Test
    fun `a malformed file wraps the underlying SerializationException`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("bad.json")
        file.writeText("whatever")
        val failing = loader("map") { _, _ -> throw SerializationException("boom") }

        val exception = assertThrows<TestLoadException> { failing.load(file) }

        assertThat(exception.message).isEqualTo("Malformed thing file: $file")
        assertThat(exception.cause).isInstanceOf(SerializationException::class.java)
    }

    @Test
    fun `a missing packaged resource names the built-in names from its index json`() {
        val exception = assertThrows<TestLoadException> { loader("map").loadResource("map/not-a-real-one.json") }

        assertThat(exception.message)
            .isEqualTo("Thing resource not found: map/not-a-real-one.json\nBuilt-in things: battletech-classic, lake-area, river-valley, test")
    }

    @Test
    fun `a missing packaged resource omits the built-in suffix when no index is packaged`() {
        val exception = assertThrows<TestLoadException> { loader("no-such-dir").loadResource("no-such-dir/x.json") }

        assertThat(exception.message).isEqualTo("Thing resource not found: no-such-dir/x.json")
    }

    @Test
    fun `builtInNames reads the packaged index`() {
        assertThat(loader("map").builtInNames()).containsExactlyInAnyOrder("battletech-classic", "lake-area", "river-valley", "test")
    }

    @Test
    fun `builtInNames is empty when no index is packaged, rather than throwing`() {
        assertThat(loader("no-such-dir").builtInNames()).isEmpty()
    }
}
