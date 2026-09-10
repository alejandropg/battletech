package tenter.screen

import com.github.ajalt.mordant.rendering.AnsiLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class MapRolePaletteTest {

    @Test
    fun `chained overrides preserve domain roles custom backgrounds and copied maps`() {
        val domain = object : ColorRole {}
        val another = object : ColorRole {}
        val customBackground = PaletteColor.Ansi16(34)
        val base = object : RolePalette by DefaultRolePalette {
            public override fun background(role: ColorRole): PaletteColor =
                if (role == ChromeRole.ACCENT) customBackground else DefaultRolePalette.background(role)
        }
        val source = mutableMapOf<ColorRole, PaletteColor>(domain to PaletteColor.Ansi16(95))
        val first = base.withOverrides(overrides = source)
        source.clear()
        val palette = first.withOverrides(overrides = mapOf(another to PaletteColor.Ansi16(96)))

        assertEquals(PaletteColor.Ansi16(95), palette.foreground(domain))
        assertEquals(PaletteColor.Ansi16(95), palette.background(domain))
        assertEquals(PaletteColor.Ansi16(96), palette.foreground(another))
        assertEquals(customBackground, palette.background(ChromeRole.ACCENT))
        assertEquals(base.foreground(ChromeRole.ACCENT), palette.foreground(ChromeRole.ACCENT))
        assertEquals(base.defaultBackground, palette.background(ChromeRole.DEFAULT))
        assertThrows<IllegalStateException> { palette.foreground(object : ColorRole {}) }
        val changed = palette.withOverrides(overrides = mapOf(ChromeRole.ACCENT to PaletteColor.Ansi16(91)))
        assertEquals(PaletteColor.Ansi16(91), changed.background(ChromeRole.ACCENT))
    }

    @Test
    fun `overrides reject mixed tiers including delegated backgrounds`() {
        assertThrows<IllegalArgumentException> {
            DefaultRolePalette.withOverrides(overrides = mapOf(ChromeRole.ACCENT to PaletteColor.TrueColor(0, 0, 0)))
        }
        val base = object : RolePalette by DefaultRolePalette {
            public override fun background(role: ColorRole): PaletteColor = PaletteColor.TrueColor(0, 0, 0)
        }
        assertThrows<IllegalArgumentException> { base.withOverrides() }
    }

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

    @Test
    fun `overrides replace chrome roles and add domain roles`() {
        val domainRole = object : ColorRole {}
        val palette = DefaultRolePalette.withOverrides(
            name = "custom",
            overrides = mapOf(
                ChromeRole.ACCENT to PaletteColor.Ansi16(94),
                domainRole to PaletteColor.Ansi16(95),
            ),
        )

        assertEquals(PaletteColor.Ansi16(95), palette.foreground(domainRole))
        assertNotEquals(DefaultRolePalette.foreground(ChromeRole.ACCENT), palette.foreground(ChromeRole.ACCENT))
        assertEquals(DefaultRolePalette.foreground(ChromeRole.DEFAULT), palette.foreground(ChromeRole.DEFAULT))
    }

    private fun chromeColors(color: PaletteColor): Map<ColorRole, PaletteColor> =
        ChromeRole.entries.associateWith { color }
}
