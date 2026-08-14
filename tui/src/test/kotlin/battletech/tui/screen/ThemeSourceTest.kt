package battletech.tui.screen

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import tenter.screen.ChromeRole
import tenter.screen.PaletteColor
import java.nio.file.Path
import kotlin.io.path.writeText

/** Mirrors `battletech.tactical.model.map.MapSourceTest` for [resolveTheme]. */
internal class ThemeSourceTest {

    @Test
    fun `every indexed built-in name loads`() {
        for (name in ThemeLoader().builtInNames()) {
            val theme = resolveTheme(name)
            assertThat(theme.foreground(ChromeRole.DEFAULT)).describedAs(name).isNotNull()
        }
    }

    @Test
    fun `the built-in index lists exactly the six shipped themes`() {
        assertThat(ThemeLoader().builtInNames())
            .containsExactlyInAnyOrder("dark", "light", "dark-256", "light-256", "dark-16", "light-16")
    }

    @Test
    fun `existing filesystem path loads its theme`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("theme.json")
        file.writeText(minimalTrueColorTheme(background = "#010203"))

        val theme = resolveTheme(file.toString())

        assertThat(theme.defaultBackground).isEqualTo(PaletteColor.TrueColor(1, 2, 3))
    }

    @Test
    fun `an existing path wins even when it shadows a built-in name`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("dark")
        file.writeText(minimalTrueColorTheme(background = "#ABCDEF"))

        val theme = resolveTheme(file.toString())

        assertThat(theme.defaultBackground).isEqualTo(PaletteColor.TrueColor(0xAB, 0xCD, 0xEF))
    }

    @Test
    fun `malformed existing filesystem path is authoritative`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("dark")
        file.writeText("{ not valid json")

        val exception = assertThrows<ThemeLoadException> { resolveTheme(file.toString()) }

        assertThat(exception.message).contains("Malformed theme file: $file")
    }

    @Test
    fun `missing packaged name names the built-in themes`() {
        val spec = "not-a-packaged-theme-for-test"

        val exception = assertThrows<ThemeLoadException> { resolveTheme(spec) }

        assertThat(exception.message)
            .isEqualTo("Theme resource not found: theme/$spec.json\nBuilt-in themes: dark, light, dark-256, light-256, dark-16, light-16")
    }

    private fun minimalTrueColorTheme(background: String): String {
        val chromeEntries = ChromeRole.entries.joinToString(",\n") { "\"${it.name}\": \"#000000\"" }
        val boardEntries = BoardRole.entries.joinToString(",\n") { "\"${it.name}\": \"#000000\"" }
        return """
        {
          "colorSpace": "truecolor",
          "background": "$background",
          "chrome": { $chromeEntries },
          "board": { $boardEntries }
        }
        """.trimIndent()
    }
}
