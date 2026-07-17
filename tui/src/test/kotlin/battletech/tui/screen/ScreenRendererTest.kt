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
        buffer.writeString(0, 0, "abc")

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
        buffer.writeString(0, 0, "hello")

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
        buffer.writeString(0, 0, "ABCD", Cell.Style(fg = Color.RED))

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
    fun `wide character written via writeString appears in output`() {
        // U+4E2D is a CJK wide character (width=2). writeString stores it in cell 0 and a
        // follow-up Cell("") in cell 1.  The renderer should include the character char and
        // the empty follow-up char (which contributes nothing), resulting in "中" appearing
        // once in the output.
        val buffer = ScreenBuffer(3, 1)
        buffer.writeString(0, 0, "中") // 中, width=2

        renderer.render(buffer)

        val out = recorder.output()
        assertTrue(out.contains("中"), "Expected wide char '中' in output: ${out.repr()}")
        // Should appear exactly once
        assertEquals(1, out.countOccurrences("中"), "Wide char should appear exactly once")
    }

    @Test
    fun `rows separated by carriage-return newline`() {
        val buffer = ScreenBuffer(2, 3)
        buffer.writeString(0, 0, "AB")
        buffer.writeString(0, 1, "CD")
        buffer.writeString(0, 2, "EF")

        renderer.render(buffer)

        val out = recorder.output()
        // rows joined with \r\n, last row has no trailing \r\n
        assertTrue(out.contains("AB\r\nCD\r\nEF"), "Expected rows joined with \\r\\n in: ${out.repr()}")
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
