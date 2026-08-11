package battletech.tui.screen

import com.github.ajalt.mordant.terminal.Terminal

// The alternate screen buffer (DECSET 1049) has no Mordant API — CursorMovements offers no such
// method, and Mordant's own CSI constant is internal — so these are raw escapes, gated below on
// terminalInfo.interactive the same way Mordant gates terminal.cursor itself.
private const val ENTER_ALT_SCREEN = "\u001B[?1049h"
private const val EXIT_ALT_SCREEN = "\u001B[?1049l"

/**
 * Prints a [ScreenBuffer] to [terminal].
 *
 * Each [render] only sends the cells that actually changed since the previous call: it keeps the
 * last-rendered buffer and diffs the new one against it, since a full repaint on every keystroke
 * (a cursor nudge, a panel toggle) is otherwise tens of KB on a large terminal. The very first
 * render, and any render after the terminal is resized or [clear] runs, has no previous frame to
 * diff against and falls back to a full repaint.
 *
 * [theme] selects which [TuiPalette] every [Color] role resolves through; `null` auto-selects
 * from [terminal]'s detected [com.github.ajalt.mordant.rendering.AnsiLevel] (see
 * [TuiTheme.autoFor]). An explicit [theme] is honored even if it exceeds that detected level —
 * only [com.github.ajalt.mordant.rendering.AnsiLevel.NONE] (the terminal cannot render color at
 * all) still suppresses every SGR tag, regardless of [theme].
 */
public class ScreenRenderer(private val terminal: Terminal, theme: TuiTheme? = null) {

    private val resolvedTheme: TuiTheme = theme ?: TuiTheme.autoFor(terminal.terminalInfo.ansiLevel)
    private val styleFactory = TextStyleFactory(TuiPalette.forTheme(resolvedTheme), terminal.terminalInfo.ansiLevel)

    // The last buffer actually sent to the terminal, or null if nothing has been sent yet (or
    // [clear] just ran) — either way, the next render() has nothing to diff against.
    private var previous: ScreenBuffer? = null

    /**
     * Sends [buffer] to the terminal, writing only what changed since the previous call.
     *
     * **Ownership**: this keeps a reference to [buffer] to diff the *next* frame against, so the
     * caller must not mutate it after handing it over — a later edit would be read as if it had
     * already been drawn, and those cells would silently never be repainted. Callers build a
     * fresh [ScreenBuffer] per frame (see `renderFrame` in `loop/RunLoop.kt`), which satisfies
     * this.
     */
    public fun render(buffer: ScreenBuffer) {
        val prev = previous
        if (prev == null || prev.width != buffer.width || prev.height != buffer.height) {
            renderFull(buffer)
        } else {
            renderDiff(buffer, prev)
        }
        previous = buffer
    }

    /**
     * Switches to the terminal's alternate screen buffer and clears it, so the game doesn't wipe
     * the user's scrollback. [cleanup] switches back, restoring exactly what was on screen before
     * [clear] ran.
     *
     * The default style's SGR tag is emitted before `clearScreen()` (which erases using whatever
     * SGR is currently active) so the freshly cleared screen paints [resolvedTheme]'s background
     * immediately — otherwise the terminal's OWN default background shows through for the instant
     * between entering the alternate screen and the first [render], which flashes the wrong color
     * whenever the theme and the terminal's own background disagree (e.g. a light theme on a
     * dark-background terminal).
     */
    public fun clear() {
        // cursor.hide() (not a raw escape) so its JVM shutdown hook stays registered: if the
        // process dies without reaching cleanup(), the cursor is still restored on exit.
        terminal.cursor.hide()
        val altScreen = if (terminal.terminalInfo.interactive) ENTER_ALT_SCREEN else ""
        val defaultStyle = styleFactory.tagsFor(Cell.Style.DEFAULT)?.open.orEmpty()
        terminal.rawPrint(altScreen + defaultStyle + terminal.cursor.getMoves { clearScreen(); setPosition(0, 0) })
        System.out.flush()
        previous = null
    }

    public fun cleanup() {
        terminal.cursor.show()
        if (terminal.terminalInfo.interactive) terminal.rawPrint(EXIT_ALT_SCREEN)
        System.out.flush()
    }

    private fun renderFull(buffer: ScreenBuffer) {
        val sb = StringBuilder()
        sb.append(terminal.cursor.getMoves { setPosition(0, 0) })
        for (y in 0 until buffer.height) {
            renderSpan(sb, buffer, y, 0, buffer.width)
            if (y < buffer.height - 1) sb.append("\r\n")
        }
        terminal.rawPrint(sb)
        System.out.flush()
    }

    /**
     * Emits only the cells where [buffer] differs from [prev] (which must be the same size),
     * as one or more `setPosition` + styled-span writes. Emits nothing at all if the two buffers
     * are identical.
     */
    private fun renderDiff(buffer: ScreenBuffer, prev: ScreenBuffer) {
        val sb = StringBuilder()
        for (y in 0 until buffer.height) {
            var x = 0
            while (x < buffer.width) {
                if (buffer.get(x, y) == prev.get(x, y)) {
                    x++
                    continue
                }
                // A wide character's continuation cell (empty char, second half of a 2-column
                // glyph) can't be printed alone — pull in the lead column at x-1 so the glyph is
                // resent as a whole. `end` still starts past the ORIGINAL x (not `start`) so a
                // clean lead never stalls progress: the loop always covers at least cell x.
                var start = x
                if (buffer.get(start, y).char.isEmpty() && start > 0) start--
                var end = x + 1
                while (end < buffer.width && buffer.get(end, y) != prev.get(end, y)) end++

                sb.append(terminal.cursor.getMoves { setPosition(start, y) })
                renderSpan(sb, buffer, y, start, end)
                x = end
            }
        }
        if (sb.isEmpty()) return
        terminal.rawPrint(sb)
        System.out.flush()
    }

    /**
     * Appends styled text for `buffer[xStart, xEnd)` on row [y] to [sb], grouping consecutive
     * same-style cells into runs and emitting only the ANSI tags needed at each run boundary.
     *
     * Closing the previous run before opening the next is skippable whenever both runs have tags
     * at all: every opening tag now establishes BOTH foreground and background explicitly (even
     * for [Color.DEFAULT], which resolves to the theme's default surface rather than leaving a
     * channel unset), so the next run's open tag always fully overwrites whatever the previous
     * run set — nothing can bleed through. Only a strikethrough-state change still needs the
     * close, since strikethrough has no "close" bundled into every open tag the way color does.
     */
    private fun renderSpan(sb: StringBuilder, buffer: ScreenBuffer, y: Int, xStart: Int, xEnd: Int) {
        var x = xStart
        var activeStyle: Cell.Style? = null
        var activeTags: TextStyleFactory.Tags? = null
        while (x < xEnd) {
            val runStyle = buffer.get(x, y).style
            val runChars = StringBuilder()
            while (x < xEnd) {
                val cell = buffer.get(x, y)
                if (cell.style != runStyle) break
                runChars.append(cell.char)
                x++
            }
            val tags = styleFactory.tagsFor(runStyle)
            if (tags == null) {
                activeTags?.let { sb.append(it.close) }
                activeStyle = null
                activeTags = null
            } else {
                val skipClose = activeStyle != null && activeStyle.strikethrough == runStyle.strikethrough
                if (!skipClose) activeTags?.let { sb.append(it.close) }
                sb.append(tags.open)
                activeStyle = runStyle
                activeTags = tags
            }
            sb.append(runChars)
        }
        activeTags?.let { sb.append(it.close) }
    }
}
