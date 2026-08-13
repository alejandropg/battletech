package tenter.screen

import tenter.text.CellWidth

/** A rect, in some [Canvas]'s local coords, that content wants kept visible — see [Canvas.markReveal]. */
public data class RevealRect(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * A rectangular, clipped, origin-translated region of a [ScreenBuffer]. All coordinates
 * passed to its methods are LOCAL: `(0, 0)` is this region's own top-left corner. Writes
 * outside `0 until width` / `0 until height` are silently dropped, so a view drawing on a
 * [Canvas] cannot touch a sibling's cells — even by mistake.
 *
 * [inset] and [region] derive smaller canvases and always clamp to the parent, so nesting
 * can only ever narrow: a child can't widen its way back out to a wider rect than it started
 * with.
 */
public class Canvas private constructor(
    private val buffer: ScreenBuffer,
    private val originX: Int,
    private val originY: Int,
    public val width: Int,
    public val height: Int,
) {

    /** The region at local ([x], [y]), clamped to fit inside this canvas. */
    public fun region(x: Int, y: Int, width: Int, height: Int): Canvas {
        val left = x.coerceIn(0, this.width)
        val top = y.coerceIn(0, this.height)
        return Canvas(
            buffer,
            originX + left,
            originY + top,
            width.coerceIn(0, this.width - left),
            height.coerceIn(0, this.height - top),
        )
    }

    /** The region left after removing [insets] from each edge, clamped to never go negative. */
    public fun inset(insets: Insets): Canvas = region(
        insets.left,
        insets.top,
        width - insets.left - insets.right,
        height - insets.top - insets.bottom,
    )

    /** @throws IndexOutOfBoundsException if ([x], [y]) is outside this canvas. */
    public fun get(x: Int, y: Int): Cell {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw IndexOutOfBoundsException("($x, $y) out of bounds for ${width}x$height canvas")
        }
        return buffer.get(originX + x, originY + y)
    }

    /** No-op if ([x], [y]) is outside this canvas. */
    public fun set(x: Int, y: Int, cell: Cell) {
        if (x < 0 || x >= width || y < 0 || y >= height) return
        buffer.set(originX + x, originY + y, cell)
    }

    /** Writes [char] in [fg], preserving whatever background is already painted at ([x], [y]). */
    public fun setFg(x: Int, y: Int, char: String, fg: ColorRole) {
        if (x < 0 || x >= width || y < 0 || y >= height) return
        val bg = buffer.get(originX + x, originY + y).style.bg
        buffer.set(originX + x, originY + y, Cell(char, Cell.Style(fg, bg)))
    }

    public fun writeString(
        x: Int,
        y: Int,
        text: String,
        style: Cell.Style = Cell.Style.DEFAULT,
    ) {
        var cx = x
        var i = 0
        while (i < text.length && cx < width) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            val w = CellWidth.of(cp)
            if (w == 0) {
                i += charCount
                continue
            }
            set(cx, y, Cell(text.substring(i, i + charCount), style))
            if (w == 2 && cx + 1 < width) {
                set(cx + 1, y, Cell("", style))
            }
            cx += w
            i += charCount
        }
    }

    /**
     * Marks the rect content most wants visible — e.g. a cursor hex, a highlighted row.
     * Stored on the backing [ScreenBuffer] in absolute coords (translated through this canvas's
     * origin, like [set]), so it survives [region]/[inset] nesting and is readable from any
     * canvas over the same buffer via [revealRect]. A scrolling container (see `Viewport`) reads
     * it back to auto-follow. Clips like [set]: a rect (partly) outside this canvas is clamped,
     * and no-ops if left empty. At most one reveal rect exists per buffer — a later call
     * overwrites an earlier one.
     */
    public fun markReveal(x: Int, y: Int, width: Int, height: Int) {
        val left = x.coerceIn(0, this.width)
        val top = y.coerceIn(0, this.height)
        val w = width.coerceIn(0, this.width - left)
        val h = height.coerceIn(0, this.height - top)
        if (w <= 0 || h <= 0) return
        buffer.reveal = RevealRect(originX + left, originY + top, w, h)
    }

    /** The rect last marked via [markReveal], translated into THIS canvas's local coords, or `null` if none. */
    public fun revealRect(): RevealRect? {
        val reveal = buffer.reveal ?: return null
        return RevealRect(reveal.x - originX, reveal.y - originY, reveal.width, reveal.height)
    }

    /**
     * Copies a [width]x[height] block from [src] at ([srcX], [srcY]) to this canvas at
     * ([destX], [destY]). Clipping is computed entirely in LOCAL coordinates against both
     * canvases' own bounds, then translated to the buffer once — so a blit can never read
     * or write past either canvas's edge, regardless of where each canvas sits in the
     * shared buffer.
     */
    public fun blit(src: Canvas, srcX: Int, srcY: Int, destX: Int, destY: Int, width: Int, height: Int) {
        val startCol = maxOf(0, -srcX, -destX)
        val endCol = minOf(width, src.width - srcX, this.width - destX)
        val count = endCol - startCol
        if (count <= 0) return
        for (row in 0 until height) {
            val sy = srcY + row
            if (sy < 0 || sy >= src.height) continue
            val dy = destY + row
            if (dy < 0 || dy >= this.height) continue
            buffer.copyCellsFrom(
                src.buffer,
                srcRow = src.originY + sy,
                srcCol = src.originX + srcX + startCol,
                destRow = originY + dy,
                destCol = originX + destX + startCol,
                count = count,
            )
        }
    }

    /** Rows from the top up to and including the last row holding a glyph or a background tint. */
    public fun contentHeight(): Int {
        for (row in height - 1 downTo 0) {
            for (col in 0 until width) {
                val cell = get(col, row)
                if (cell.char != " " || cell.style.bg != UiRole.DEFAULT) return row + 1
            }
        }
        return 0
    }

    public companion object {
        /** A canvas over the whole of [buffer]. */
        public fun of(buffer: ScreenBuffer): Canvas = Canvas(buffer, 0, 0, buffer.width, buffer.height)

        /** A detached canvas over its own backing buffer — for measuring or scrolling content off-screen. */
        public fun offscreen(width: Int, height: Int): Canvas = of(ScreenBuffer(width, height))
    }
}
