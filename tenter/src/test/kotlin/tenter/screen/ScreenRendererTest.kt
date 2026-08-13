package tenter.screen

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A minimal, fully-authored [RolePalette] for exercising [ScreenRenderer]'s own mechanics —
 * diffing, run coalescing, alt-screen switching — independent of any host application's actual
 * theme. Every [UiRole] is resolved so [ScreenRenderer] can be exercised with an arbitrary
 * [Cell.Style], not just the handful of roles individual tests reference.
 */
private fun rgb(r: Int, g: Int, b: Int) = PaletteColor.TrueColor(r, g, b)

private object FixturePalette : RolePalette {
    override val defaultBackground: PaletteColor = rgb(16, 20, 24)

    override fun resolve(role: ColorRole): PaletteColor = when (role) {
        is UiRole -> when (role) {
            UiRole.DEFAULT -> rgb(221, 226, 229)
            UiRole.TEXT_PRIMARY -> rgb(241, 243, 245)
            UiRole.TEXT_MUTED -> rgb(166, 173, 180)
            UiRole.TEXT_SUBTLE -> rgb(76, 80, 84)
            UiRole.ACCENT -> rgb(255, 209, 102)
            UiRole.INFO -> rgb(119, 212, 232)
            UiRole.SUCCESS -> rgb(139, 209, 124)
            UiRole.WARNING -> rgb(243, 211, 106)
            UiRole.DANGER -> rgb(255, 153, 153)
            UiRole.DRAFT -> rgb(166, 173, 180)
            UiRole.DISABLED -> rgb(137, 145, 152)
            UiRole.PANEL_BORDER -> rgb(114, 191, 114)
        }
        else -> error("FixturePalette only resolves UiRole, got: $role")
    }
}

private object AltFixturePalette : RolePalette {
    override val defaultBackground: PaletteColor = rgb(248, 245, 238)

    override fun resolve(role: ColorRole): PaletteColor = when (role) {
        is UiRole -> rgb(32, 36, 40)
        else -> error("AltFixturePalette only resolves UiRole, got: $role")
    }
}

internal class ScreenRendererTest {

    private val recorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR)
    private val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, terminalInterface = recorder)
    private val renderer = ScreenRenderer(terminal, FixturePalette)

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
    fun `default cell emits the palette's explicit foreground and background truecolor sequences`() {
        // UiRole.DEFAULT resolves to a real color — there is no "unstyled" cell on an
        // ANSI-capable terminal.
        val buffer = ScreenBuffer(5, 1)
        Canvas.of(buffer).writeString(0, 0, "hello")

        renderer.render(buffer)

        val out = recorder.output()
        assertTrue(out.contains("hello"), "Expected 'hello' in output: ${out.repr()}")
        assertTrue(out.contains("38;2;221;226;229"), "Expected the fixture's default foreground: ${out.repr()}")
        assertTrue(out.contains("48;2;16;20;24"), "Expected the fixture's default background: ${out.repr()}")
    }

    @Test
    fun `default cell emits a different palette's sequences when constructed with it`() {
        val altRecorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR)
        val altTerminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, terminalInterface = altRecorder)
        val altRenderer = ScreenRenderer(altTerminal, AltFixturePalette)

        val buffer = ScreenBuffer(5, 1)
        Canvas.of(buffer).writeString(0, 0, "hello")
        altRenderer.render(buffer)

        val out = altRecorder.output()
        assertTrue(out.contains("38;2;32;36;40"), "Expected AltFixturePalette's foreground: ${out.repr()}")
        assertTrue(out.contains("48;2;248;245;238"), "Expected AltFixturePalette's default background: ${out.repr()}")
    }

    @Test
    fun `colored run emits one style per run not one per cell`() {
        // 4 cells all with fg=DANGER — they should be wrapped in a single SGR open+close pair
        val buffer = ScreenBuffer(4, 1)
        Canvas.of(buffer).writeString(0, 0, "ABCD", Cell.Style(fg = UiRole.DANGER))

        renderer.render(buffer)

        val out = recorder.output()
        // The characters must appear contiguous — no SGR codes between them
        assertTrue(out.contains("ABCD"), "Expected 'ABCD' to appear contiguously in output: ${out.repr()}")

        // Count closing/reset sequences for fg: ESC[39m is always the fg-reset code, regardless
        // of the foreground's own color space (truecolor/256/16) — see AnsiCodes.fgColorReset.
        // UiRole.DEFAULT now sets a real background too, so every style sets both channels and
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
            buffer.set(x, 0, Cell("X", Cell.Style(fg = UiRole.DANGER)))
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
        Canvas.of(buffer).writeString(0, 0, "XYZ", Cell.Style(fg = UiRole.DANGER, strikethrough = true))

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
        // Every open tag now sets BOTH channels explicitly (UiRole.DEFAULT resolves to real
        // colors too), so the run-length skip-close optimization never needs to special-case
        // DEFAULT — see ScreenRenderer.renderSpan's KDoc. Regression guard: a cell that follows a
        // differently-colored one must still get its own explicit fg AND bg, not inherit either
        // channel from the previous run.
        val buffer = ScreenBuffer(2, 1)
        buffer.set(0, 0, Cell("A", Cell.Style(fg = UiRole.DANGER, bg = UiRole.SUCCESS)))
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
        // now (UiRole.DEFAULT sets real colors), so "AB" and "CD" are no longer literally
        // adjacent to the \r\n — a close tag sits between the text and the separator — so this
        // checks ordering and separator count rather than one contiguous substring.
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
        val plainRenderer = ScreenRenderer(plainTerminal, FixturePalette)

        plainRenderer.clear()
        plainRenderer.cleanup()

        assertTrue(
            !plainRecorder.output().contains("1049"),
            "Expected no alt-screen switching on a non-interactive terminal: ${plainRecorder.output().repr()}",
        )
    }

    @Test
    fun `clear paints the palette's default background before erasing, not the terminal's own`() {
        // Regression guard for the cross-fade flash described in ScreenRenderer.clear's KDoc: the
        // default-style SGR tag must be emitted before the erase-display sequence, so ED erases
        // using the palette's background rather than whatever the terminal itself defaults to.
        val altRecorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR)
        val altTerminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, terminalInterface = altRecorder)
        val altRenderer = ScreenRenderer(altTerminal, AltFixturePalette)

        altRenderer.clear()

        val out = altRecorder.output()
        val bgIndex = out.indexOf("48;2;248;245;238")
        val eraseIndex = out.indexOf("[2J")
        assertTrue(bgIndex >= 0, "Expected AltFixturePalette's default background before clearing: ${out.repr()}")
        assertTrue(eraseIndex >= 0, "Expected an erase-display sequence: ${out.repr()}")
        assertTrue(bgIndex < eraseIndex, "Expected the background tag before the erase sequence: ${out.repr()}")
    }

    @Test
    fun `AnsiLevel NONE emits no SGR codes at all`() {
        val noneRecorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE)
        val noneTerminal = Terminal(ansiLevel = AnsiLevel.NONE, terminalInterface = noneRecorder)
        val noneRenderer = ScreenRenderer(noneTerminal, FixturePalette)

        val buffer = ScreenBuffer(3, 1)
        Canvas.of(buffer).writeString(0, 0, "abc", Cell.Style(fg = UiRole.DANGER, strikethrough = true))
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
