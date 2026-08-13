package battletech.tui.screen

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ScreenBuffer
import tenter.screen.ScreenRenderer

/**
 * Verifies BattleTech's own themes render through [tenter.screen.ScreenRenderer] with their
 * authored, theme-specific values — no downsampling between tiers, no nearest-color
 * approximation. [tenter.screen.ScreenRendererTest] already covers the renderer's generic
 * mechanics (diffing, run coalescing, alt-screen switching) against a palette fixture; this class
 * only re-checks what's specific to `tui`'s six concrete [RolePalette]s and [TuiTheme.autoFor].
 */
internal class ScreenRendererTest {

    @Test
    fun `default cell emits the dark theme's explicit foreground and background truecolor sequences`() {
        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR)
        val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, terminalInterface = recorder)
        val renderer = ScreenRenderer(terminal, TuiTheme.DARK.toRolePalette())

        val buffer = ScreenBuffer(5, 1)
        Canvas.of(buffer).writeString(0, 0, "hello")
        renderer.render(buffer)

        val out = recorder.output()
        assertTrue(out.contains("hello"), "Expected 'hello' in output: ${out.repr()}")
        assertTrue(out.contains("38;2;221;226;229"), "Expected the dark theme's default foreground (#DDE2E5): ${out.repr()}")
        assertTrue(out.contains("48;2;16;20;24"), "Expected the dark theme's default background (#101418): ${out.repr()}")
    }

    @Test
    fun `default cell emits the exact light theme sequences when configured with LIGHT`() {
        val lightRecorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR)
        val lightTerminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, terminalInterface = lightRecorder)
        val lightRenderer = ScreenRenderer(lightTerminal, TuiTheme.LIGHT.toRolePalette())

        val buffer = ScreenBuffer(5, 1)
        Canvas.of(buffer).writeString(0, 0, "hello")
        lightRenderer.render(buffer)

        val out = lightRecorder.output()
        assertTrue(out.contains("38;2;32;36;40"), "Expected the light theme's default foreground (#202428): ${out.repr()}")
        assertTrue(out.contains("48;2;248;245;238"), "Expected the light theme's default background (#F8F5EE): ${out.repr()}")
    }

    @Test
    fun `DARK_256 emits indexed sequences and no truecolor sequence`() {
        val idxRecorder = TerminalRecorder(ansiLevel = AnsiLevel.ANSI256)
        val idxTerminal = Terminal(ansiLevel = AnsiLevel.ANSI256, terminalInterface = idxRecorder)
        val idxRenderer = ScreenRenderer(idxTerminal, TuiTheme.DARK_256.toRolePalette())

        val buffer = ScreenBuffer(1, 1)
        buffer.set(0, 0, Cell("X", Cell.Style(fg = BoardRole.BOARD_ACTIVE)))
        idxRenderer.render(buffer)

        val out = idxRecorder.output()
        assertTrue(out.contains("38;5;221"), "Expected indexed foreground 221 for BOARD_ACTIVE in dark-256: ${out.repr()}")
        assertFalse(out.contains("38;2") || out.contains("48;2"), "Expected no truecolor sequence in dark-256: ${out.repr()}")
    }

    @Test
    fun `DARK_16 emits basic SGR codes and no indexed or truecolor sequence`() {
        val basicRecorder = TerminalRecorder(ansiLevel = AnsiLevel.ANSI16)
        val basicTerminal = Terminal(ansiLevel = AnsiLevel.ANSI16, terminalInterface = basicRecorder)
        val basicRenderer = ScreenRenderer(basicTerminal, TuiTheme.DARK_16.toRolePalette())

        val buffer = ScreenBuffer(1, 1)
        buffer.set(0, 0, Cell("X", Cell.Style(fg = BoardRole.BOARD_ACTIVE)))
        basicRenderer.render(buffer)

        val out = basicRecorder.output()
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
        val idxRecorder = TerminalRecorder(ansiLevel = AnsiLevel.ANSI256)
        val idxTerminal = Terminal(ansiLevel = AnsiLevel.ANSI256, terminalInterface = idxRecorder)
        val idxRenderer = ScreenRenderer(idxTerminal, TuiTheme.DARK_256.toRolePalette())

        val buffer = ScreenBuffer(1, 1)
        Canvas.of(buffer).writeString(0, 0, "X")
        idxRenderer.render(buffer)

        val out = idxRecorder.output()
        assertTrue(out.contains("48;5;233"), "Expected DARK_256's own authored default background index 233: ${out.repr()}")
        assertFalse(out.contains("48;5;16"), "Expected NOT the nearest-cube approximation of DARK's truecolor default: ${out.repr()}")
    }

    @Test
    fun `TuiTheme autoFor maps each detected AnsiLevel to the theme this module renders with`() {
        val truecolorRecorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR)
        val truecolorTerminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, terminalInterface = truecolorRecorder)
        val truecolorRenderer = ScreenRenderer(truecolorTerminal, TuiTheme.autoFor(AnsiLevel.TRUECOLOR).toRolePalette())
        val truecolorBuffer = ScreenBuffer(1, 1)
        Canvas.of(truecolorBuffer).writeString(0, 0, "X")
        truecolorRenderer.render(truecolorBuffer)
        assertTrue(
            truecolorRecorder.output().contains("48;2;16;20;24"),
            "Expected TRUECOLOR to auto-select DARK: ${truecolorRecorder.output().repr()}",
        )

        val idxRecorder = TerminalRecorder(ansiLevel = AnsiLevel.ANSI256)
        val idxTerminal = Terminal(ansiLevel = AnsiLevel.ANSI256, terminalInterface = idxRecorder)
        val idxRenderer = ScreenRenderer(idxTerminal, TuiTheme.autoFor(AnsiLevel.ANSI256).toRolePalette())
        val idxBuffer = ScreenBuffer(1, 1)
        Canvas.of(idxBuffer).writeString(0, 0, "X")
        idxRenderer.render(idxBuffer)
        assertTrue(
            idxRecorder.output().contains("48;5;233"),
            "Expected ANSI256 to auto-select DARK_256: ${idxRecorder.output().repr()}",
        )

        val basicRecorder = TerminalRecorder(ansiLevel = AnsiLevel.ANSI16)
        val basicTerminal = Terminal(ansiLevel = AnsiLevel.ANSI16, terminalInterface = basicRecorder)
        val basicRenderer = ScreenRenderer(basicTerminal, TuiTheme.autoFor(AnsiLevel.ANSI16).toRolePalette())
        val basicBuffer = ScreenBuffer(1, 1)
        Canvas.of(basicBuffer).writeString(0, 0, "X")
        basicRenderer.render(basicBuffer)
        assertTrue(
            basicRecorder.output().contains("37;40"),
            "Expected ANSI16 to auto-select DARK_16: ${basicRecorder.output().repr()}",
        )
    }

    private fun String.repr(): String = this.replace("\u001B", "ESC").replace("\r", "\\r").replace("\n", "\\n")
}
