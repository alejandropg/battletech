package io.archinaut.battletech.tui.screen

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import io.archinaut.tenter.color.ColorSpace
import io.archinaut.tenter.color.StandardRole

/** Mirrors `io.archinaut.battletech.tactical.model.map.MapFileTest` for [ThemeFile.toTheme]'s validation. */
internal class ThemeFileTest {

    private val allStandard = StandardRole.entries.associate { it.name to "#000000" }
    private val allApp = AppRole.entries.associate { it.name to "#000000" }
    private val allBoard = BoardRole.entries.associate { it.name to "#000000" }
    private val allHeatScale = HeatScaleRole.entries.associate { it.name to "#000000" }

    @Test
    fun `a complete truecolor file parses into a named Theme`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "#010203",
            standard = allStandard,
            app = allApp,
            board = allBoard,
            heatScale = allHeatScale,
        )

        val theme = file.toTheme("test")

        assertThat(theme.toString()).isEqualTo("test")
        assertThat(theme.foreground(StandardRole.DANGER)).isEqualTo(io.archinaut.tenter.color.TerminalColor.TrueColor(0, 0, 0))
    }

    @Test
    fun `a missing standard role names it`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "#000000",
            standard = allStandard - StandardRole.DANGER.name,
            app = allApp,
            board = allBoard,
            heatScale = allHeatScale,
        )

        val exception = assertThrows<ThemeLoadException> { file.toTheme("test") }
        assertThat(exception.message).contains("missing standard roles").contains("DANGER")
    }

    @Test
    fun `a missing board role names it`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "#000000",
            standard = allStandard,
            app = allApp,
            board = allBoard - BoardRole.PLAYER_1.name,
            heatScale = allHeatScale,
        )

        val exception = assertThrows<ThemeLoadException> { file.toTheme("test") }
        assertThat(exception.message).contains("missing board roles").contains("PLAYER_1")
    }

    @Test
    fun `a missing heat scale role names it`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "#000000",
            standard = allStandard,
            app = allApp,
            board = allBoard,
            heatScale = allHeatScale - HeatScaleRole.CURRENT_BG.name,
        )

        val exception = assertThrows<ThemeLoadException> { file.toTheme("test") }
        assertThat(exception.message).contains("missing heatScale roles").contains("CURRENT_BG")
    }

    @Test
    fun `an unknown standard role name is rejected`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "#000000",
            standard = allStandard + ("NOT_A_ROLE" to "#000000"),
            app = allApp,
            board = allBoard,
            heatScale = allHeatScale,
        )

        val exception = assertThrows<ThemeLoadException> { file.toTheme("test") }
        assertThat(exception.message).contains("unknown standard role").contains("NOT_A_ROLE")
    }

    @Test
    fun `an unknown board role name is rejected`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "#000000",
            standard = allStandard,
            app = allApp,
            board = allBoard + ("NOT_A_ROLE" to "#000000"),
            heatScale = allHeatScale,
        )

        val exception = assertThrows<ThemeLoadException> { file.toTheme("test") }
        assertThat(exception.message).contains("unknown board role").contains("NOT_A_ROLE")
    }

    @Test
    fun `an unknown heat scale role name is rejected`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "#000000",
            standard = allStandard,
            app = allApp,
            board = allBoard,
            heatScale = allHeatScale + ("NOT_A_ROLE" to "#000000"),
        )

        val exception = assertThrows<ThemeLoadException> { file.toTheme("test") }
        assertThat(exception.message).contains("unknown heatScale role").contains("NOT_A_ROLE")
    }

    @Test
    fun `a missing app role names it`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "#000000",
            standard = allStandard,
            app = allApp - AppRole.DRAFT.name,
            board = allBoard,
            heatScale = allHeatScale,
        )

        val exception = assertThrows<ThemeLoadException> { file.toTheme("test") }
        assertThat(exception.message).contains("missing app roles").contains("DRAFT")
    }

    @Test
    fun `DRAFT in the standard table is rejected, since it is an application role`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "#000000",
            standard = allStandard + ("DRAFT" to "#000000"),
            app = allApp,
            board = allBoard,
            heatScale = allHeatScale,
        )

        val exception = assertThrows<ThemeLoadException> { file.toTheme("test") }
        assertThat(exception.message).contains("unknown standard role").contains("DRAFT")
    }

    @Test
    fun `a malformed hex value is rejected`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.TRUECOLOR,
            background = "not-a-color",
            standard = allStandard,
            app = allApp,
            board = allBoard,
            heatScale = allHeatScale,
        )

        assertThrows<ThemeLoadException> { file.toTheme("test") }
    }

    @Test
    fun `an xterm index out of 16 to 255 is rejected`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.ANSI256,
            background = "999",
            standard = StandardRole.entries.associate { it.name to "233" },
            app = allApp,
            board = BoardRole.entries.associate { it.name to "233" },
            heatScale = HeatScaleRole.entries.associate { it.name to "233" },
        )

        assertThrows<ThemeLoadException> { file.toTheme("test") }
    }

    @Test
    fun `an ansi16 code outside 30 to 37 or 90 to 97 is rejected`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.ANSI16,
            background = "50",
            standard = StandardRole.entries.associate { it.name to "30" },
            app = allApp,
            board = BoardRole.entries.associate { it.name to "30" },
            heatScale = HeatScaleRole.entries.associate { it.name to "30" },
        )

        assertThrows<ThemeLoadException> { file.toTheme("test") }
    }

    @Test
    fun `a non-numeric ansi256 value is rejected`() {
        val file = ThemeFile(
            colorSpace = ColorSpace.ANSI256,
            background = "bright-red",
            standard = StandardRole.entries.associate { it.name to "233" },
            app = allApp,
            board = BoardRole.entries.associate { it.name to "233" },
            heatScale = HeatScaleRole.entries.associate { it.name to "233" },
        )

        assertThrows<ThemeLoadException> { file.toTheme("test") }
    }
}
