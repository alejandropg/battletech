package io.archinaut.battletech.tui.screen

import com.github.ajalt.mordant.rendering.AnsiLevel
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.palette.ColorRole
import io.archinaut.tenter.palette.PaletteColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies BattleTech's own themes carry their authored, theme-specific colors — no downsampling
 * between tiers, no nearest-color approximation. How a palette becomes escape sequences is
 * tenter's job and tested there; this class only checks what's specific to `tui`'s built-in
 * [Theme]s and [defaultThemeName].
 */
internal class ThemeColorsTest {

    private val allRoles: List<ColorRole> = ChromeRole.entries + BoardRole.entries + UiRole.entries + HeatScaleRole.entries

    @Test
    fun `the dark theme's default colors are its authored truecolor values`() {
        val dark = resolveTheme("dark")

        assertEquals(PaletteColor.TrueColor(221, 226, 229), dark.foreground(ChromeRole.DEFAULT))
        assertEquals(PaletteColor.TrueColor(16, 20, 24), dark.defaultBackground)
    }

    @Test
    fun `the light theme's default colors are its authored truecolor values`() {
        val light = resolveTheme("light")

        assertEquals(PaletteColor.TrueColor(32, 36, 40), light.foreground(ChromeRole.DEFAULT))
        assertEquals(PaletteColor.TrueColor(248, 245, 238), light.defaultBackground)
    }

    @Test
    fun `dark-256 uses only indexed colors`() {
        val dark256 = resolveTheme("dark-256")

        assertEquals(PaletteColor.Xterm256(221), dark256.foreground(BoardRole.BOARD_ACTIVE))
        assertTrue(allRoles.all { dark256.foreground(it) is PaletteColor.Xterm256 }, "dark-256 has a non-indexed color")
    }

    @Test
    fun `dark-16 uses only basic colors`() {
        val dark16 = resolveTheme("dark-16")

        assertEquals(PaletteColor.Ansi16(93), dark16.foreground(BoardRole.BOARD_ACTIVE))
        assertTrue(allRoles.all { dark16.foreground(it) is PaletteColor.Ansi16 }, "dark-16 has a non-basic color")
    }

    @Test
    fun `no theme's colors are a nearest-color approximation of another's`() {
        // Regression guard for the deleted downsample() path: DARK's default background #101418
        // nearest-maps to xterm-256 index 16 under the standard cube mapping, but DARK_256 is
        // authored independently and must keep its OWN index (233).
        assertEquals(PaletteColor.Xterm256(233), resolveTheme("dark-256").defaultBackground)
    }

    @Test
    fun `defaultThemeName maps each detected AnsiLevel to the matching dark theme`() {
        assertEquals(PaletteColor.TrueColor(16, 20, 24), resolveTheme(defaultThemeName(AnsiLevel.TRUECOLOR)).defaultBackground)
        assertEquals(PaletteColor.Xterm256(233), resolveTheme(defaultThemeName(AnsiLevel.ANSI256)).defaultBackground)

        val basic = resolveTheme(defaultThemeName(AnsiLevel.ANSI16))
        assertEquals(PaletteColor.Ansi16(37), basic.foreground(ChromeRole.DEFAULT))
        assertEquals(PaletteColor.Ansi16(30), basic.defaultBackground)
    }
}
