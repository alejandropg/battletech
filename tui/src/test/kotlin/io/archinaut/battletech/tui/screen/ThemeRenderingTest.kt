package io.archinaut.battletech.tui.screen

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.TerminalRecorder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Cell
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.terminal.Terminal
import io.archinaut.tenter.widget.Widget
import com.github.ajalt.mordant.terminal.Terminal as MordantTerminal

/**
 * Verifies BattleTech's own themes render through tenter's [Terminal] with their authored,
 * theme-specific values — no downsampling between tiers, no nearest-color approximation.
 * Tenter's own tests already cover the terminal's generic mechanics (diffing, run coalescing,
 * alt-screen switching) against a palette fixture; this class only re-checks what's specific to
 * `tui`'s six built-in [Theme]s and [defaultThemeName].
 */
internal class ThemeRenderingTest {

    @Test
    fun `default cell emits the dark theme's explicit foreground and background truecolor sequences`() {
        val out = drawnOutput(AnsiLevel.TRUECOLOR, "dark", width = 5) { it.setString(0, 0, "hello") }

        assertTrue(out.contains("hello"), "Expected 'hello' in output: ${out.repr()}")
        assertTrue(out.contains("38;2;221;226;229"), "Expected the dark theme's default foreground (#DDE2E5): ${out.repr()}")
        assertTrue(out.contains("48;2;16;20;24"), "Expected the dark theme's default background (#101418): ${out.repr()}")
    }

    @Test
    fun `default cell emits the exact light theme sequences when configured with LIGHT`() {
        val out = drawnOutput(AnsiLevel.TRUECOLOR, "light", width = 5) { it.setString(0, 0, "hello") }

        assertTrue(out.contains("38;2;32;36;40"), "Expected the light theme's default foreground (#202428): ${out.repr()}")
        assertTrue(out.contains("48;2;248;245;238"), "Expected the light theme's default background (#F8F5EE): ${out.repr()}")
    }

    @Test
    fun `DARK_256 emits indexed sequences and no truecolor sequence`() {
        val out = drawnOutput(AnsiLevel.ANSI256, "dark-256") {
            it.set(0, 0, Cell("X", Style(fg = BoardRole.BOARD_ACTIVE)))
        }

        assertTrue(out.contains("38;5;221"), "Expected indexed foreground 221 for BOARD_ACTIVE in dark-256: ${out.repr()}")
        assertFalse(out.contains("38;2") || out.contains("48;2"), "Expected no truecolor sequence in dark-256: ${out.repr()}")
    }

    @Test
    fun `DARK_16 emits basic SGR codes and no indexed or truecolor sequence`() {
        val out = drawnOutput(AnsiLevel.ANSI16, "dark-16") {
            it.set(0, 0, Cell("X", Style(fg = BoardRole.BOARD_ACTIVE)))
        }

        assertTrue(out.contains("93"), "Expected basic SGR code 93 for BOARD_ACTIVE in dark-16: ${out.repr()}")
        assertFalse(
            out.contains("38;5") || out.contains("48;5") || out.contains("38;2") || out.contains("48;2"),
            "Expected no indexed or truecolor sequence in dark-16: ${out.repr()}",
        )
    }

    @Test
    fun `no theme's output is a nearest-color approximation of another's`() {
        // Regression guard for the deleted downsample() path: DARK's default background #101418
        // nearest-maps to xterm-256 index 16 under the standard cube mapping, but DARK_256 is
        // authored independently and must emit its OWN index (233), never the truecolor theme's
        // nearest neighbor.
        val out = drawnOutput(AnsiLevel.ANSI256, "dark-256") { it.setString(0, 0, "X") }

        assertTrue(out.contains("48;5;233"), "Expected DARK_256's own authored default background index 233: ${out.repr()}")
        assertFalse(out.contains("48;5;16"), "Expected NOT the nearest-cube approximation of DARK's truecolor default: ${out.repr()}")
    }

    @Test
    fun `defaultThemeName maps each detected AnsiLevel to the theme this module renders with`() {
        val truecolor = drawnOutput(AnsiLevel.TRUECOLOR, defaultThemeName(AnsiLevel.TRUECOLOR)) { it.setString(0, 0, "X") }
        assertTrue(truecolor.contains("48;2;16;20;24"), "Expected TRUECOLOR to auto-select dark: ${truecolor.repr()}")

        val indexed = drawnOutput(AnsiLevel.ANSI256, defaultThemeName(AnsiLevel.ANSI256)) { it.setString(0, 0, "X") }
        assertTrue(indexed.contains("48;5;233"), "Expected ANSI256 to auto-select dark-256: ${indexed.repr()}")

        val basic = drawnOutput(AnsiLevel.ANSI16, defaultThemeName(AnsiLevel.ANSI16)) { it.setString(0, 0, "X") }
        assertTrue(basic.contains("37;40"), "Expected ANSI16 to auto-select dark-16: ${basic.repr()}")
    }

    /** Draws one [width]x1 frame painted by [paint], themed [themeName], at [level]; returns what was sent. */
    private fun drawnOutput(level: AnsiLevel, themeName: String, width: Int = 1, paint: (Canvas) -> Unit): String {
        val recorder = TerminalRecorder(ansiLevel = level, width = width, height = 1)
        val terminal = Terminal(MordantTerminal(ansiLevel = level, terminalInterface = recorder), resolveTheme(themeName))
        val widget = object : Widget {
            override fun render(canvas: Canvas) = paint(canvas)
        }
        terminal.draw { it.renderWidget(widget, it.area) }
        return recorder.output()
    }

    private fun String.repr(): String = this.replace("\u001B", "ESC").replace("\r", "\\r").replace("\n", "\\n")
}
