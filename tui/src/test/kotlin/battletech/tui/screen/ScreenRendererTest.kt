package battletech.tui.screen

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import org.junit.jupiter.api.Assertions.assertEquals
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

        // setPosition(0, 0) emits ESC[1;1H (1-indexed row;col)
        assertTrue(
            recorder.output().startsWith("[1;1H"),
            "Expected output to start with ESC[1;1H but was: ${recorder.output().take(20).repr()}"
        )
    }

    @Test
    fun `unstyled text is written without ANSI codes`() {
        val buffer = ScreenBuffer(5, 1)
        Canvas.of(buffer).writeString(0, 0, "hello")

        renderer.render(buffer)

        val out = recorder.output()
        // The text must appear verbatim somewhere after the cursor-home prefix
        assertTrue(out.contains("hello"), "Expected 'hello' in output")
        // No color codes in a plain-text run (only the cursor home sequence)
        val afterHome = out.removePrefix("[1;1H")
        assertTrue(
            afterHome == "hello",
            "After cursor-home the only content should be 'hello', got: ${afterHome.repr()}"
        )
    }

    @Test
    fun `colored run emits one style per run not one per cell`() {
        // 4 cells all with fg=RED — they should be wrapped in a single SGR open+close pair
        val buffer = ScreenBuffer(4, 1)
        Canvas.of(buffer).writeString(0, 0, "ABCD", Cell.Style(fg = Color.RED))

        renderer.render(buffer)

        val out = recorder.output()
        // The characters must appear contiguous — no SGR codes between them
        assertTrue(out.contains("ABCD"), "Expected 'ABCD' to appear contiguously in output: ${out.repr()}")

        // Count closing/reset sequences for fg: ESC[39m (Ansi16 fg-color-reset)
        // Each run emits exactly one close tag; with one run we expect exactly one.
        val resetSeq = "[39m"
        val resetCount = out.countOccurrences(resetSeq)
        assertEquals(1, resetCount, "Expected exactly 1 fg-reset sequence for a single run, got $resetCount in: ${out.repr()}")
    }

    @Test
    fun `per-cell styling would produce four resets but run-length produces one`() {
        // Contrast: 4 consecutive RED cells → 1 reset (run-length) vs 4 resets (per-cell, old).
        val buffer = ScreenBuffer(4, 1)
        for (x in 0 until 4) {
            buffer.set(x, 0, Cell("X", Cell.Style(fg = Color.RED)))
        }

        renderer.render(buffer)

        val out = recorder.output()
        val resetSeq = "[39m"
        val resetCount = out.countOccurrences(resetSeq)
        assertEquals(1, resetCount, "4 same-style cells should produce exactly 1 reset, got $resetCount")
    }

    @Test
    fun `strikethrough style emits strikethrough SGR once per run`() {
        // 3 consecutive cells with fg=RED + strikethrough — Mordant folds both attributes into
        // one compound SGR open (ESC[31;9m ... ESC[39;29m), and one run should emit exactly one.
        val buffer = ScreenBuffer(3, 1)
        Canvas.of(buffer).writeString(0, 0, "XYZ", Cell.Style(fg = Color.RED, strikethrough = true))

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
        // rows joined with \r\n, last row has no trailing \r\n
        assertTrue(out.contains("AB\r\nCD\r\nEF"), "Expected rows joined with \\r\\n in: ${out.repr()}")
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

    // ---- ansiLevel downsampling (rawPrint bypasses Mordant's own downsampling, see TextStyleFactory) ----

    @Test
    fun `ANSI16 terminal downsamples a truecolor tint to a 4-bit code`() {
        val ansi16Recorder = TerminalRecorder(ansiLevel = AnsiLevel.ANSI16)
        val ansi16Terminal = Terminal(ansiLevel = AnsiLevel.ANSI16, terminalInterface = ansi16Recorder)
        val ansi16Renderer = ScreenRenderer(ansi16Terminal)

        val buffer = ScreenBuffer(1, 1)
        buffer.set(0, 0, Cell("X", Cell.Style(bg = Color.WOODS_LIGHT_BG)))
        ansi16Renderer.render(buffer)

        val out = ansi16Recorder.output()
        assertTrue(out.contains("X"), "Expected the cell content in output: ${out.repr()}")
        assertTrue(
            Regex("\\[[\\d;]+m").containsMatchIn(out),
            "Expected some SGR code for the background tint: ${out.repr()}",
        )
        assertTrue(
            !out.contains("48;2") && !out.contains("48;5"),
            "Expected no truecolor (48;2) or 256-color (48;5) SGR at AnsiLevel.ANSI16: ${out.repr()}",
        )
    }

    @Test
    fun `AnsiLevel NONE emits no SGR codes at all`() {
        val noneRecorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE)
        val noneTerminal = Terminal(ansiLevel = AnsiLevel.NONE, terminalInterface = noneRecorder)
        val noneRenderer = ScreenRenderer(noneTerminal)

        val buffer = ScreenBuffer(3, 1)
        Canvas.of(buffer).writeString(0, 0, "abc", Cell.Style(fg = Color.RED, strikethrough = true))
        noneRenderer.render(buffer)

        val out = noneRecorder.output()
        assertTrue(out.contains("abc"), "Expected plain text content: ${out.repr()}")
        assertTrue(
            !Regex("\\[[\\d;]+m").containsMatchIn(out),
            "Expected no SGR codes at all at AnsiLevel.NONE: ${out.repr()}",
        )
    }

    // ---- helpers ----

    private fun String.repr(): String = this.replace("", "ESC").replace("\r", "\\r").replace("\n", "\\n")

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
