package tenter.screen

import com.github.ajalt.mordant.terminal.Terminal

/**
 * Prints a [ScreenBuffer] to [terminal].
 *
 * Each [render] only sends the cells that actually changed since the previous call: it keeps the
 * last-rendered buffer and diffs the new one against it, since a full repaint on every keystroke
 * (a cursor nudge, a panel toggle) is otherwise tens of KB on a large terminal. The very first
 * render, and any render after the terminal is resized or [clear] runs, has no previous frame to
 * diff against and falls back to a full repaint.
 *
 * [palette] resolves every [ColorRole] a caller draws with to a concrete color; picking *which*
 * palette to use (a light/dark choice, detecting the terminal's color support, letting the user
 * override it) is the host application's job, not this renderer's — construct the [RolePalette]
 * before handing it here. The terminal's own [com.github.ajalt.mordant.rendering.AnsiLevel] is
 * still read directly off [terminal] for exactly one purpose: at
 * [com.github.ajalt.mordant.rendering.AnsiLevel.NONE] every SGR tag is suppressed regardless of
 * [palette], since the terminal cannot render color at all.
 */
public class ScreenRenderer(private val terminal: Terminal, palette: RolePalette) {

    private val styleTagCache = StyleTagCache(palette, terminal.terminalInfo.ansiLevel)

    // A private snapshot of the last buffer actually sent to the terminal, or null if nothing
    // has been sent yet (or [clear] just ran). Caller-owned buffers may be safely reused.
    private var previous: ScreenBuffer? = null

    /**
     * Sends [buffer] to the terminal, writing only what changed since the previous call.
     *
     * The renderer snapshots the submitted frame after output succeeds, so the caller may mutate
     * and reuse [buffer] for the next frame. The terminal and cursor lifecycle belongs to
     * [tenter.terminal.withScreen]; this renderer only paints and invalidates frames.
     */
    public fun render(buffer: ScreenBuffer) {
        val prev = previous
        if (prev == null || prev.width != buffer.width || prev.height != buffer.height) {
            renderFull(buffer, clearStale = prev != null && (buffer.width < prev.width || buffer.height < prev.height))
        } else {
            renderDiff(buffer, prev)
        }
        previous = buffer.snapshot()
    }

    /**
     * Clears the current drawing destination and invalidates the diff snapshot. The scoped
     * [tenter.terminal.withScreen] operation owns alternate-screen and cursor lifecycle; calling
     * this method directly never starts or ends an alternate-screen session.
     *
     * The default style's SGR tag is emitted before `clearScreen()` (which erases using whatever
     * SGR is currently active) so the cleared screen uses [palette]'s background immediately.
     */
    public fun clear() {
        val defaultStyle = styleTagCache.tagsFor(Cell.Style.DEFAULT)?.open.orEmpty()
        terminal.rawPrint(defaultStyle + terminal.cursor.getMoves { clearScreen(); setPosition(0, 0) })
        previous = null
    }

    private fun renderFull(buffer: ScreenBuffer, clearStale: Boolean) {
        val sb = StringBuilder()
        sb.append(terminal.cursor.getMoves {
            if (clearStale) clearScreen()
            setPosition(0, 0)
        })
        for (y in 0 until buffer.height) {
            renderSpan(sb, buffer, y, 0, buffer.width)
            if (y < buffer.height - 1) sb.append("\r\n")
        }
        terminal.rawPrint(sb)
    }

    /**
     * Emits only the cells where [buffer] differs from [prev] (which must be the same size),
     * as one or more `setPosition` + styled-span writes. Emits nothing at all if the two buffers
     * are identical.
     */
    private fun renderDiff(buffer: ScreenBuffer, prev: ScreenBuffer) {
        val sb = StringBuilder()
        for (y in 0 until buffer.height) {
            val dirty = mutableListOf<IntRange>()
            for (x in 0 until buffer.width) {
                if (buffer.get(x, y) == prev.get(x, y)) continue
                val start = minOf(buffer.glyphStart(x, y), prev.glyphStart(x, y))
                val end = maxOf(buffer.glyphEndExclusive(x, y), prev.glyphEndExclusive(x, y))
                val last = dirty.lastOrNull()
                if (last != null && start <= last.last + 1) {
                    dirty[dirty.lastIndex] = last.first..maxOf(last.last, end - 1)
                } else {
                    dirty += start..(end - 1)
                }
            }
            for (span in dirty) {
                sb.append(terminal.cursor.getMoves { setPosition(span.first, y) })
                renderSpan(sb, buffer, y, span.first, span.last + 1)
            }
        }
        if (sb.isEmpty()) return
        terminal.rawPrint(sb)
    }

    /**
     * Appends styled text for `buffer[xStart, xEnd)` on row [y] to [sb], grouping consecutive
     * same-style cells into runs and emitting only the ANSI tags needed at each run boundary.
     *
     * Closing the previous run before opening the next is skippable whenever both runs have tags
     * at all: every opening tag now establishes BOTH foreground and background explicitly (even
     * for [ChromeRole.DEFAULT], which resolves to the palette's default surface rather than leaving a
     * channel unset), so the next run's open tag always fully overwrites whatever the previous
     * run set — nothing can bleed through. Only a strikethrough-state change still needs the
     * close, since strikethrough has no "close" bundled into every open tag the way color does.
     */
    private fun renderSpan(sb: StringBuilder, buffer: ScreenBuffer, y: Int, xStart: Int, xEnd: Int) {
        var x = xStart
        var activeStyle: Cell.Style? = null
        var activeTags: StyleTagCache.Tags? = null
        while (x < xEnd) {
            val runStyle = buffer.get(x, y).style
            val runChars = StringBuilder()
            while (x < xEnd) {
                val cell = buffer.get(x, y)
                if (cell.style != runStyle) break
                runChars.append(cell.char)
                x++
            }
            val tags = styleTagCache.tagsFor(runStyle)
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
