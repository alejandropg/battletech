package battletech.tui.screen

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ScreenRendererTest {

    private val recorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR)
    private val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, terminalInterface = recorder)
    private val renderer = ScreenRenderer(terminal)

    @Test
    fun `output starts with cursor home sequence`() {
        val buffer = ScreenBuffer(3, 1)
        Canvas.of(buffer).writeString(0, 0, "abc")

        renderer.render(buffer)

        // setPosition(0, 0) emits ESC[1;1H (1-indexed row;col) — the literal ESC byte is part of
        // the prefix, not just the bracket text that follows it.
        assertTrue(
            recorder.output().startsWith("[1;1H"),
            "Expected output to start with ESC[1;1H but was: ${recorder.output().take(20).repr()}"
        )
    }

    @Test
    fun `default cell emits the dark theme's explicit foreground and background truecolor sequences`() {
        // Color.DEFAULT resolves to real colors now — unlike the old literal-color scheme, there
        // is no "unstyled" cell on an ANSI-capable terminal.
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
        val lightRenderer = ScreenRenderer(lightTerminal, TuiTheme.LIGHT)

        val buffer = ScreenBuffer(5, 1)
        Canvas.of(buffer).writeString(0, 0, "hello")
        lightRenderer.render(buffer)

        val out = lightRecorder.output()
        assertTrue(out.contains("38;2;32;36;40"), "Expected the light theme's default foreground (#202428): ${out.repr()}")
        assertTrue(out.contains("48;2;248;245;238"), "Expected the light theme's default background (#F8F5EE): ${out.repr()}")
    }

    @Test
    fun `colored run emits one style per run not one per cell`() {
        // 4 cells all with fg=DANGER — they should be wrapped in a single SGR open+close pair
        val buffer = ScreenBuffer(4, 1)
        Canvas.of(buffer).writeString(0, 0, "ABCD", Cell.Style(fg = Color.DANGER))

        renderer.render(buffer)

        val out = recorder.output()
        // The characters must appear contiguous — no SGR codes between them
        assertTrue(out.contains("ABCD"), "Expected 'ABCD' to appear contiguously in output: ${out.repr()}")

        // Count closing/reset sequences for fg: ESC[39m is always the fg-reset code, regardless
        // of the foreground's own color space (truecolor/256/16) — see AnsiCodes.fgColorReset.
        // Color.DEFAULT now sets a real background too, so every style sets both channels and
        // the close tag is always the combined "39;49m" (fg+bg reset in one SGR), never a bare
        // "39m" — see ScreenRenderer.renderSpan's KDoc. Each run emits exactly one close tag;
        // with one run we expect exactly one.
        val resetSeq = "39;49m"
        val resetCount = out.countOccurrences(resetSeq)
        assertEquals(1, resetCount, "Expected exactly 1 fg+bg-reset sequence for a single run, got $resetCount in: ${out.repr()}")
    }

    @Test
    fun `per-cell styling would produce four resets but run-length produces one`() {
        // Contrast: 4 consecutive DANGER cells → 1 reset (run-length) vs 4 resets (per-cell, old).
        val buffer = ScreenBuffer(4, 1)
        for (x in 0 until 4) {
            buffer.set(x, 0, Cell("X", Cell.Style(fg = Color.DANGER)))
        }

        renderer.render(buffer)

        val out = recorder.output()
        val resetSeq = "39;49m"
        val resetCount = out.countOccurrences(resetSeq)
        assertEquals(1, resetCount, "4 same-style cells should produce exactly 1 reset, got $resetCount")
    }

    @Test
    fun `strikethrough style emits strikethrough SGR once per run`() {
        // 3 consecutive cells with fg=DANGER + strikethrough — Mordant folds both attributes into
        // one compound SGR open, and one run should emit exactly one.
        val buffer = ScreenBuffer(3, 1)
        Canvas.of(buffer).writeString(0, 0, "XYZ", Cell.Style(fg = Color.DANGER, strikethrough = true))

        renderer.render(buffer)

        val out = recorder.output()
        assertTrue(out.contains("XYZ"), "Expected 'XYZ' to appear contiguously in output: ${out.repr()}")

        // ";9m" is the strikethrough-on code compounded onto the fg-open sequence;
        // ";29m" is strikethrough-off compounded onto the fg-reset sequence.
        val strikeOnCount = out.countOccurrences(";9m")
        val strikeOffCount = out.countOccurrences(";29m")
        assertEquals(
            1,
            strikeOnCount,
            "Expected exactly 1 strikethrough-on sequence for a single run, got $strikeOnCount in: ${out.repr()}",
        )
        assertEquals(
            1,
            strikeOffCount,
            "Expected exactly 1 strikethrough-off sequence for a single run, got $strikeOffCount in: ${out.repr()}",
        )
    }

    @Test
    fun `transitions between default and semantic roles do not leak either color channel`() {
        // Every open tag now sets BOTH channels explicitly (Color.DEFAULT resolves to real colors
        // too), so the run-length skip-close optimization never needs to special-case DEFAULT —
        // see ScreenRenderer.renderSpan's KDoc. Regression guard: a cell that follows a
        // differently-colored one must still get its own explicit fg AND bg, not inherit either
        // channel from the previous run.
        val buffer = ScreenBuffer(2, 1)
        buffer.set(0, 0, Cell("A", Cell.Style(fg = Color.DANGER, bg = Color.SUCCESS)))
        buffer.set(1, 0, Cell("B", Cell.Style.DEFAULT))

        renderer.render(buffer)

        val out = recorder.output()
        val betweenAAndB = out.substringAfter("A").substringBefore("B")
        assertTrue(
            betweenAAndB.contains("38;2;221;226;229"),
            "Expected the DEFAULT cell to explicitly set its own foreground, not inherit DANGER's: ${out.repr()}",
        )
        assertTrue(
            betweenAAndB.contains("48;2;16;20;24"),
            "Expected the DEFAULT cell to explicitly set its own background, not inherit SUCCESS's: ${out.repr()}",
        )
    }

    @Test
    fun `wide character written via writeString appears in output`() {
        // U+4E2D is a CJK wide character (width=2). writeString stores it in cell 0 and a
        // follow-up Cell("") in cell 1.  The renderer should include the character char and
        // the empty follow-up char (which contributes nothing), resulting in "中" appearing
        // once in the output.
        val buffer = ScreenBuffer(3, 1)
        Canvas.of(buffer).writeString(0, 0, "中") // 中, width=2

        renderer.render(buffer)

        val out = recorder.output()
        assertTrue(out.contains("中"), "Expected wide char '中' in output: ${out.repr()}")
        // Should appear exactly once
        assertEquals(1, out.countOccurrences("中"), "Wide char should appear exactly once")
    }

    @Test
    fun `rows separated by carriage-return newline`() {
        val buffer = ScreenBuffer(2, 3)
        Canvas.of(buffer).writeString(0, 0, "AB")
        Canvas.of(buffer).writeString(0, 1, "CD")
        Canvas.of(buffer).writeString(0, 2, "EF")

        renderer.render(buffer)

        val out = recorder.output()
        // Rows joined with \r\n, last row has no trailing \r\n. Each row is its own styled run
        // now (Color.DEFAULT sets real colors), so "AB" and "CD" are no longer literally adjacent
        // to the \r\n — a close tag sits between the text and the separator — so this checks
        // ordering and separator count rather than one contiguous substring.
        val firstBreak = out.indexOf("\r\n")
        val secondBreak = out.indexOf("\r\n", firstBreak + 1)
        assertTrue(firstBreak >= 0 && secondBreak >= 0, "Expected two \\r\\n row separators in: ${out.repr()}")
        assertEquals(2, out.countOccurrences("\r\n"), "Expected exactly two row separators (3 rows, no trailing one): ${out.repr()}")
        assertTrue(out.indexOf("AB") in 0..<firstBreak, "Expected 'AB' before the first row break: ${out.repr()}")
        assertTrue(out.indexOf("CD") in firstBreak..<secondBreak, "Expected 'CD' between the two row breaks: ${out.repr()}")
        assertTrue(out.indexOf("EF") > secondBreak, "Expected 'EF' after the second row break: ${out.repr()}")
    }

    // ---- dirty-cell diffing (render() keeps the last-sent buffer and only sends what changed) ----

    @Test
    fun `second render of an identical buffer emits nothing`() {
        val first = ScreenBuffer(5, 2)
        Canvas.of(first).writeString(0, 0, "hello")
        renderer.render(first)

        // A fresh, separately-allocated buffer with the same content — render() diffs by value,
        // not by instance identity.
        val second = ScreenBuffer(5, 2)
        Canvas.of(second).writeString(0, 0, "hello")
        recorder.clearOutput()
        renderer.render(second)

        assertEquals("", recorder.output(), "An unchanged frame must produce no output at all")
    }

    @Test
    fun `single changed cell emits only that cell, not the rest of the row`() {
        val first = ScreenBuffer(5, 1)
        Canvas.of(first).writeString(0, 0, "hello")
        renderer.render(first)

        // Only column 2 differs ('l' -> 't').
        val second = ScreenBuffer(5, 1)
        Canvas.of(second).writeString(0, 0, "hetlo")
        recorder.clearOutput()
        renderer.render(second)

        val out = recorder.output()
        assertTrue(out.contains("t"), "Expected the changed cell 't' in the diff output: ${out.repr()}")
        assertTrue(
            !out.contains("hello") && !out.contains("hetlo"),
            "Expected only the changed cell to be sent, not the whole row: ${out.repr()}",
        )
    }

    @Test
    fun `a changed wide character re-emits its lead glyph, not a lone continuation cell`() {
        // "a中b": a(width 1) + 中(width 2, CJK) + b(width 1) — writeString stores 中's lead
        // glyph at column 1 and an empty Cell("") continuation at column 2.
        val first = ScreenBuffer(4, 1)
        Canvas.of(first).writeString(0, 0, "a中b")
        renderer.render(first)

        // Swap the wide glyph for a different one of the same width, at the same position.
        val second = ScreenBuffer(4, 1)
        Canvas.of(second).writeString(0, 0, "a日b")
        recorder.clearOutput()
        renderer.render(second)

        val out = recorder.output()
        assertTrue(out.contains("日"), "Expected the new wide glyph to be resent whole: ${out.repr()}")
        assertTrue(!out.contains("中"), "Expected the old glyph to be gone: ${out.repr()}")
    }

    @Test
    fun `a terminal size change forces a full repaint even though content is unchanged`() {
        val first = ScreenBuffer(3, 1)
        Canvas.of(first).writeString(0, 0, "abc")
        renderer.render(first)

        // Same content, different dimensions.
        val resized = ScreenBuffer(4, 1)
        Canvas.of(resized).writeString(0, 0, "abc")
        recorder.clearOutput()
        renderer.render(resized)

        assertTrue(
            recorder.output().startsWith("[1;1H"),
            "Expected a full repaint (cursor-home) after a size change, got: ${recorder.output().take(20).repr()}",
        )
    }

    // ---- alternate screen buffer ----

    @Test
    fun `clear enters the alternate screen and cleanup leaves it`() {
        renderer.clear()
        assertTrue(
            recorder.output().contains("\u001B[?1049h"),
            "clear() must enter the alternate screen: ${recorder.output().repr()}",
        )

        recorder.clearOutput()
        renderer.cleanup()
        assertTrue(
            recorder.output().contains("\u001B[?1049l"),
            "cleanup() must leave the alternate screen: ${recorder.output().repr()}",
        )
    }

    @Test
    fun `alt-screen sequences always carry their ESC prefix`() {
        // Regression guard for a real bug: a raw ESC byte in the source is easy to drop in an
        // edit, and the sequence then lands on the user's shell as the literal text "[?1049l"
        // after quitting. Counting bare vs ESC-prefixed occurrences catches exactly that.
        renderer.clear()
        renderer.cleanup()
        val out = recorder.output()

        assertEquals(
            out.countOccurrences("[?1049h"),
            out.countOccurrences("\u001B[?1049h"),
            "Every [?1049h must be ESC-prefixed, got a bare one in: ${out.repr()}",
        )
        assertEquals(
            out.countOccurrences("[?1049l"),
            out.countOccurrences("\u001B[?1049l"),
            "Every [?1049l must be ESC-prefixed, got a bare one in: ${out.repr()}",
        )
    }

    @Test
    fun `a non-interactive terminal gets no alt-screen escapes at all`() {
        val plainRecorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE)
        val plainTerminal = Terminal(ansiLevel = AnsiLevel.NONE, terminalInterface = plainRecorder)
        val plainRenderer = ScreenRenderer(plainTerminal)

        plainRenderer.clear()
        plainRenderer.cleanup()

        assertTrue(
            !plainRecorder.output().contains("1049"),
            "Expected no alt-screen switching on a non-interactive terminal: ${plainRecorder.output().repr()}",
        )
    }

    @Test
    fun `clear paints the theme's default background before erasing, not the terminal's own`() {
        // Regression guard for the cross-fade flash described in ScreenRenderer.clear's KDoc: the
        // default-style SGR tag must be emitted before the erase-display sequence, so ED erases
        // using the theme's background rather than whatever the terminal itself defaults to.
        val lightRecorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR)
        val lightTerminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, terminalInterface = lightRecorder)
        val lightRenderer = ScreenRenderer(lightTerminal, TuiTheme.LIGHT)

        lightRenderer.clear()

        val out = lightRecorder.output()
        val bgIndex = out.indexOf("48;2;248;245;238")
        val eraseIndex = out.indexOf("[2J")
        assertTrue(bgIndex >= 0, "Expected the light theme's default background before clearing: ${out.repr()}")
        assertTrue(eraseIndex >= 0, "Expected an erase-display sequence: ${out.repr()}")
        assertTrue(bgIndex < eraseIndex, "Expected the background tag before the erase sequence: ${out.repr()}")
    }

    // ---- theme encoding (no downsampling — each theme is authored natively for its color space) ----

    @Test
    fun `DARK_256 emits indexed sequences and no truecolor sequence`() {
        val idxRecorder = TerminalRecorder(ansiLevel = AnsiLevel.ANSI256)
        val idxTerminal = Terminal(ansiLevel = AnsiLevel.ANSI256, terminalInterface = idxRecorder)
        val idxRenderer = ScreenRenderer(idxTerminal, TuiTheme.DARK_256)

        val buffer = ScreenBuffer(1, 1)
        buffer.set(0, 0, Cell("X", Cell.Style(fg = Color.ACCENT)))
        idxRenderer.render(buffer)

        val out = idxRecorder.output()
        assertTrue(out.contains("38;5;221"), "Expected indexed foreground 221 for ACCENT in dark-256: ${out.repr()}")
        assertFalse(out.contains("38;2") || out.contains("48;2"), "Expected no truecolor sequence in dark-256: ${out.repr()}")
    }

    @Test
    fun `DARK_16 emits basic SGR codes and no indexed or truecolor sequence`() {
        val basicRecorder = TerminalRecorder(ansiLevel = AnsiLevel.ANSI16)
        val basicTerminal = Terminal(ansiLevel = AnsiLevel.ANSI16, terminalInterface = basicRecorder)
        val basicRenderer = ScreenRenderer(basicTerminal, TuiTheme.DARK_16)

        val buffer = ScreenBuffer(1, 1)
        buffer.set(0, 0, Cell("X", Cell.Style(fg = Color.ACCENT)))
        basicRenderer.render(buffer)

        val out = basicRecorder.output()
        assertTrue(out.contains("93"), "Expected basic SGR code 93 for ACCENT in dark-16: ${out.repr()}")
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
        val idxRenderer = ScreenRenderer(idxTerminal, TuiTheme.DARK_256)

        val buffer = ScreenBuffer(1, 1)
        Canvas.of(buffer).writeString(0, 0, "X")
        idxRenderer.render(buffer)

        val out = idxRecorder.output()
        assertTrue(out.contains("48;5;233"), "Expected DARK_256's own authored default background index 233: ${out.repr()}")
        assertFalse(out.contains("48;5;16"), "Expected NOT the nearest-cube approximation of DARK's truecolor default: ${out.repr()}")
    }

    @Test
    fun `auto-selection maps each detected AnsiLevel to the theme named in Themes`() {
        val truecolorRecorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR)
        val truecolorTerminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, terminalInterface = truecolorRecorder)
        val truecolorRenderer = ScreenRenderer(truecolorTerminal)
        val truecolorBuffer = ScreenBuffer(1, 1)
        Canvas.of(truecolorBuffer).writeString(0, 0, "X")
        truecolorRenderer.render(truecolorBuffer)
        assertTrue(
            truecolorRecorder.output().contains("48;2;16;20;24"),
            "Expected TRUECOLOR to auto-select DARK: ${truecolorRecorder.output().repr()}",
        )

        val idxRecorder = TerminalRecorder(ansiLevel = AnsiLevel.ANSI256)
        val idxTerminal = Terminal(ansiLevel = AnsiLevel.ANSI256, terminalInterface = idxRecorder)
        val idxRenderer = ScreenRenderer(idxTerminal)
        val idxBuffer = ScreenBuffer(1, 1)
        Canvas.of(idxBuffer).writeString(0, 0, "X")
        idxRenderer.render(idxBuffer)
        assertTrue(
            idxRecorder.output().contains("48;5;233"),
            "Expected ANSI256 to auto-select DARK_256: ${idxRecorder.output().repr()}",
        )

        val basicRecorder = TerminalRecorder(ansiLevel = AnsiLevel.ANSI16)
        val basicTerminal = Terminal(ansiLevel = AnsiLevel.ANSI16, terminalInterface = basicRecorder)
        val basicRenderer = ScreenRenderer(basicTerminal)
        val basicBuffer = ScreenBuffer(1, 1)
        Canvas.of(basicBuffer).writeString(0, 0, "X")
        basicRenderer.render(basicBuffer)
        assertTrue(
            basicRecorder.output().contains("37;40"),
            "Expected ANSI16 to auto-select DARK_16: ${basicRecorder.output().repr()}",
        )
    }

    @Test
    fun `an explicit theme overrides auto-selection even when it exceeds the detected level`() {
        // Terminal reports ANSI256, but LIGHT (a truecolor theme) is supplied explicitly — the
        // explicit theme wins verbatim; only AnsiLevel.NONE suppresses output.
        val idxRecorder = TerminalRecorder(ansiLevel = AnsiLevel.ANSI256)
        val idxTerminal = Terminal(ansiLevel = AnsiLevel.ANSI256, terminalInterface = idxRecorder)
        val overriddenRenderer = ScreenRenderer(idxTerminal, TuiTheme.LIGHT)

        val buffer = ScreenBuffer(1, 1)
        Canvas.of(buffer).writeString(0, 0, "X")
        overriddenRenderer.render(buffer)

        val out = idxRecorder.output()
        assertTrue(out.contains("48;2;248;245;238"), "Expected LIGHT's truecolor default background despite the ANSI256 terminal: ${out.repr()}")
    }

    @Test
    fun `AnsiLevel NONE emits no SGR codes at all`() {
        val noneRecorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE)
        val noneTerminal = Terminal(ansiLevel = AnsiLevel.NONE, terminalInterface = noneRecorder)
        val noneRenderer = ScreenRenderer(noneTerminal)

        val buffer = ScreenBuffer(3, 1)
        Canvas.of(buffer).writeString(0, 0, "abc", Cell.Style(fg = Color.DANGER, strikethrough = true))
        noneRenderer.render(buffer)

        val out = noneRecorder.output()
        assertTrue(out.contains("abc"), "Expected plain text content: ${out.repr()}")
        assertTrue(
            !Regex("\\[[\\d;]+m").containsMatchIn(out),
            "Expected no SGR codes at all at AnsiLevel.NONE: ${out.repr()}",
        )
    }

    // ---- helpers ----

    private fun String.repr(): String = this.replace("\u001B", "ESC").replace("\r", "\\r").replace("\n", "\\n")

    private fun String.countOccurrences(sub: String): Int {
        if (sub.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            idx = this.indexOf(sub, idx)
            if (idx == -1) break
            count++
            idx += sub.length
        }
        return count
    }
}
