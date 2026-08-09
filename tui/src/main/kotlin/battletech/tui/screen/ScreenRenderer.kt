package battletech.tui.screen

import com.github.ajalt.mordant.terminal.Terminal

/**
 * Prints a [ScreenBuffer] to [terminal].
 *
 * Each [render] only sends the cells that actually changed since the previous call: it keeps the
 * last-rendered buffer and diffs the new one against it, since a full repaint on every keystroke
 * (a cursor nudge, a panel toggle) is otherwise tens of KB on a large terminal. The very first
 * render, and any render after the terminal is resized or [clear] runs, has no previous frame to
 * diff against and falls back to a full repaint.
 */
public class ScreenRenderer(private val terminal: Terminal) {

    private val styleFactory = TextStyleFactory(terminal.terminalInfo.ansiLevel)

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

    public fun clear() {
        terminal.cursor.hide()
        terminal.cursor.move {
            clearScreen()
            setPosition(0, 0)
        }
        System.out.flush()
        previous = null
    }

    public fun cleanup() {
        terminal.cursor.show()
        terminal.cursor.move {
            clearScreen()
            setPosition(0, 0)
        }
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
     * Closing the previous run before opening the next is only skippable when the next run's
     * colors fully overwrite whatever the previous run set: a channel the previous style left
     * non-default (fg or bg) must also be non-default in the next style, or its color would bleed
     * through — SGR color-set codes are absolute, but Mordant only emits one for a channel that
     * differs from default, so a style that leaves a channel at default doesn't reset it.
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
                val skipClose = activeStyle != null &&
                    (activeStyle.fg == Color.DEFAULT || runStyle.fg != Color.DEFAULT) &&
                    (activeStyle.bg == Color.DEFAULT || runStyle.bg != Color.DEFAULT) &&
                    activeStyle.strikethrough == runStyle.strikethrough
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
