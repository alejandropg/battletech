package battletech.tui.screen

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tenter.screen.ChromeRole

/** Mirrors `battletech.tactical.model.map.MapFileTest` for [ThemeFile.toTheme]'s validation. */
internal class ThemeFileTest {

    private val allChrome = ChromeRole.entries.associate { it.name to "#000000" }
    private val allBoard = BoardRole.entries.associate { it.name to "#000000" }

    @Test
    fun `a complete truecolor file parses into a named Theme`() {
        val file = ThemeFile(colorSpace = ColorSpace.TRUECOLOR, background = "#010203", chrome = allChrome, board = allBoard)

        val theme = file.toTheme("test")

        assertThat(theme.toString()).isEqualTo("test")
        assertThat(theme.foreground(ChromeRole.DANGER)).isEqualTo(tenter.screen.PaletteColor.TrueColor(0, 0, 0))
    }

    @Test
    fun `a missing chrome role names it`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "#000000",
            chrome = allChrome - ChromeRole.DANGER.name,
            board = allBoard,
        )

        val exception = assertThrows<ThemeLoadException> { file.toTheme("test") }
        assertThat(exception.message).contains("missing chrome roles").contains("DANGER")
    }

    @Test
    fun `a missing board role names it`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "#000000",
            chrome = allChrome,
            board = allBoard - BoardRole.PLAYER_1.name,
        )

        val exception = assertThrows<ThemeLoadException> { file.toTheme("test") }
        assertThat(exception.message).contains("missing board roles").contains("PLAYER_1")
    }

    @Test
    fun `an unknown chrome role name is rejected`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "#000000",
            chrome = allChrome + ("NOT_A_ROLE" to "#000000"),
            board = allBoard,
        )

        val exception = assertThrows<ThemeLoadException> { file.toTheme("test") }
        assertThat(exception.message).contains("unknown chrome role").contains("NOT_A_ROLE")
    }

    @Test
    fun `an unknown board role name is rejected`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "#000000",
            chrome = allChrome,
            board = allBoard + ("NOT_A_ROLE" to "#000000"),
        )

        val exception = assertThrows<ThemeLoadException> { file.toTheme("test") }
        assertThat(exception.message).contains("unknown board role").contains("NOT_A_ROLE")
    }

    @Test
    fun `a malformed hex value is rejected`() {
        val file = ThemeFile(colorSpace = ColorSpace.TRUECOLOR, background = "not-a-color", chrome = allChrome, board = allBoard)

        assertThrows<ThemeLoadException> { file.toTheme("test") }
    }

    @Test
    fun `an xterm index out of 16 to 255 is rejected`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.ANSI256,
            background = "999",
            chrome = ChromeRole.entries.associate { it.name to "233" },
            board = BoardRole.entries.associate { it.name to "233" },
        )

        assertThrows<ThemeLoadException> { file.toTheme("test") }
    }

    @Test
    fun `an ansi16 code outside 30 to 37 or 90 to 97 is rejected`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.ANSI16,
            background = "50",
            chrome = ChromeRole.entries.associate { it.name to "30" },
            board = BoardRole.entries.associate { it.name to "30" },
        )

        assertThrows<ThemeLoadException> { file.toTheme("test") }
    }

    @Test
    fun `a non-numeric ansi256 value is rejected`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.ANSI256,
            background = "bright-red",
            chrome = ChromeRole.entries.associate { it.name to "233" },
            board = BoardRole.entries.associate { it.name to "233" },
        )

        assertThrows<ThemeLoadException> { file.toTheme("test") }
    }
}
