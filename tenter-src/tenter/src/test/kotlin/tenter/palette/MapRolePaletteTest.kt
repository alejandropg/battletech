package tenter.palette

import com.github.ajalt.mordant.rendering.AnsiLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class MapRolePaletteTest {

    private data class Fixed(override val color: PaletteColor) : FixedColorRole

    @Test
    fun `a map palette requires every toolkit chrome role before rendering`() {
        val exception = assertThrows<IllegalArgumentException> {
            MapRolePalette(
                name = "test",
                level = AnsiLevel.TRUECOLOR,
                defaultBackground = PaletteColor.TrueColor(0, 0, 0),
                colors = emptyMap(),
            )
        }

        assertEquals(true, exception.message?.contains("chrome roles"))
    }

    @Test
    fun `a map palette validates its declared color tier`() {
        assertThrows<IllegalArgumentException> {
            MapRolePalette(
                name = "test",
                level = AnsiLevel.TRUECOLOR,
                defaultBackground = PaletteColor.Ansi16(30),
                colors = chromeColors(PaletteColor.TrueColor(255, 255, 255)),
            )
        }
    }

    @Test
    fun `a map palette rejects NONE, which names a terminal rather than an authoring tier`() {
        val exception = assertThrows<IllegalArgumentException> {
            MapRolePalette(
                name = "test",
                level = AnsiLevel.NONE,
                defaultBackground = PaletteColor.Ansi16(30),
                colors = chromeColors(PaletteColor.Ansi16(97)),
            )
        }

        assertEquals(true, exception.message?.contains("AnsiLevel.NONE"))
    }

    @Test
    fun `a map palette copies source colors and resolves semantic roles`() {
        val accent = PaletteColor.TrueColor(255, 0, 0)
        val source = chromeColors(PaletteColor.TrueColor(255, 255, 255)).toMutableMap()
        source[ChromeRole.ACCENT] = accent
        val palette = MapRolePalette(
            name = "test",
            level = AnsiLevel.TRUECOLOR,
            defaultBackground = PaletteColor.TrueColor(0, 0, 0),
            colors = source,
        )

        source[ChromeRole.ACCENT] = PaletteColor.TrueColor(0, 255, 0)

        assertEquals(accent, palette.foreground(ChromeRole.ACCENT))
        assertThrows<IllegalStateException> {
            palette.foreground(Fixed(PaletteColor.TrueColor(255, 0, 0)))
        }
    }

    private fun chromeColors(color: PaletteColor): Map<ColorRole, PaletteColor> =
        ChromeRole.entries.associateWith { color }
}
