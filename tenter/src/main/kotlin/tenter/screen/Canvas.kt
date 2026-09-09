package tenter.screen

import tenter.text.textClusters

/** A rect, in some [Canvas]'s local coords, that content wants kept visible — see [Canvas.markReveal]. */
public data class RevealRect(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * A rectangular, clipped, origin-translated region of a [ScreenBuffer]. All coordinates
 * passed to its methods are LOCAL: `(0, 0)` is this region's own top-left corner. Writes
 * outside `0 until width` / `0 until height` are silently dropped, so a view drawing on a
 * [Canvas] cannot touch a sibling's cells — even by mistake.
 *
 * [inset] and [region] derive smaller canvases by rectangle intersection, so nesting can only
 * ever narrow: a child can't widen its way back out to a wider rect than it started with.
 */
public class Canvas private constructor(
    private val buffer: ScreenBuffer,
    private val originX: Int,
    private val originY: Int,
    public val width: Int,
    public val height: Int,
) {

    /** The intersection of the requested rectangle with this canvas. */
    public fun region(x: Int, y: Int, width: Int, height: Int): Canvas {
        val horizontal = intersection(x, width, this.width)
        val vertical = intersection(y, height, this.height)
        return Canvas(
            buffer,
            originX + horizontal.first,
            originY + vertical.first,
            horizontal.second - horizontal.first,
            vertical.second - vertical.first,
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
        buffer.writeCell(originX + x, originY + y, cell, originX, originY, originX + width, originY + height)
    }

    /** Writes [char] in [fg], preserving whatever background is already painted at ([x], [y]). */
    public fun setFg(x: Int, y: Int, char: String, fg: ColorRole) {
        if (x < 0 || x >= width || y < 0 || y >= height) return
        val bg = buffer.get(originX + x, originY + y).style.bg
        buffer.writeCell(
            originX + x,
            originY + y,
            Cell(char, Cell.Style(fg, bg)),
            originX,
            originY,
            originX + width,
            originY + height,
        )
    }

    public fun writeString(
        x: Int,
        y: Int,
        text: String,
        style: Cell.Style = Cell.Style.DEFAULT,
    ) {
        var column = x
        for (cluster in textClusters(text)) {
            if (cluster.width == 0) continue
            buffer.writeCell(
                originX + column,
                originY + y,
                Cell(cluster.drawableText, style),
                originX,
                originY,
                originX + width,
                originY + height,
            )
            column += cluster.width
        }
    }

    /**
     * Marks the rect content most wants visible — e.g. a selection cursor, a highlighted row.
     * Stored on the backing [ScreenBuffer] in absolute coords (translated through this canvas's
     * origin, like [set]), so it survives [region]/[inset] nesting and is readable from any
     * canvas over the same buffer via [revealRect]. A scrolling container (see `Viewport`) reads
     * it back to auto-follow. Clips like [set]: a rect (partly) outside this canvas is clamped,
     * and no-ops if left empty. At most one reveal rect exists per buffer — a later call
     * overwrites an earlier one.
     */
    public fun markReveal(x: Int, y: Int, width: Int, height: Int) {
        val horizontal = intersection(x, width, this.width)
        val vertical = intersection(y, height, this.height)
        if (horizontal.first == horizontal.second || vertical.first == vertical.second) return
        buffer.reveal = RevealRect(
            originX + horizontal.first,
            originY + vertical.first,
            horizontal.second - horizontal.first,
            vertical.second - vertical.first,
        )
    }

    /** The rect last marked via [markReveal], translated into THIS canvas's local coords, or `null` if none. */
    public fun revealRect(): RevealRect? {
        val reveal = buffer.reveal ?: return null
        return RevealRect(reveal.x - originX, reveal.y - originY, reveal.width, reveal.height)
    }

    /** Clears the reveal request in this canvas's backing frame. Used by prepared frame scopes. */
    internal fun clearReveal() {
        buffer.reveal = null
    }

    /**
     * Copies a [width]x[height] block from [src] at ([srcX], [srcY]) to this canvas at
     * ([destX], [destY]). Clipping is computed entirely in LOCAL coordinates against both
     * canvases' own bounds, then translated to the buffer once — so a blit can never read
     * or write past either canvas's edge, regardless of where each canvas sits in the
     * shared buffer.
     */
    public fun blit(src: Canvas, srcX: Int, srcY: Int, destX: Int, destY: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val startCol = maxOf(0L, -srcX.toLong(), -destX.toLong())
        val endCol = minOf(width.toLong(), src.width.toLong() - srcX, this.width.toLong() - destX)
        val startRow = maxOf(0L, -srcY.toLong(), -destY.toLong())
        val endRow = minOf(height.toLong(), src.height.toLong() - srcY, this.height.toLong() - destY)
        if (startCol >= endCol || startRow >= endRow) return

        val source = src.buffer.snapshotCells()
        val clipLeft = originX + destX + startCol.toInt()
        val clipTop = originY + destY + startRow.toInt()
        val clipRight = originX + destX + endCol.toInt()
        val clipBottom = originY + destY + endRow.toInt()
        for (row in startRow until endRow) {
            var column = startCol
            while (column < endCol) {
                val sourceX = src.originX + srcX + column.toInt()
                val sourceY = src.originY + srcY + row.toInt()
                val stored = source[sourceY][sourceX]
                val completeWide = !stored.continuation && stored.width == 2 &&
                    column + 1 < endCol && sourceX + 1 < src.originX + src.width &&
                    source[sourceY][sourceX + 1].continuation
                val destinationX = originX + destX + column.toInt()
                val destinationY = originY + destY + row.toInt()
                if (completeWide) {
                    buffer.writeCell(
                        destinationX,
                        destinationY,
                        stored.cell,
                        clipLeft,
                        clipTop,
                        clipRight,
                        clipBottom,
                    )
                    column += 2
                } else {
                    val cell = if (stored.continuation || stored.width == 2) {
                        Cell(" ", stored.cell.style)
                    } else {
                        stored.cell
                    }
                    buffer.writeCell(
                        destinationX,
                        destinationY,
                        cell,
                        clipLeft,
                        clipTop,
                        clipRight,
                        clipBottom,
                    )
                    column++
                }
            }
        }
    }

    private fun intersection(start: Int, length: Int, limit: Int): Pair<Int, Int> {
        val requestedStart = start.toLong()
        val requestedEnd = requestedStart + length.toLong()
        val left = requestedStart.coerceIn(0L, limit.toLong())
        val right = requestedEnd.coerceIn(left, limit.toLong())
        return left.toInt() to right.toInt()
    }

    public companion object {
        /** A canvas over the whole of [buffer]. */
        public fun of(buffer: ScreenBuffer): Canvas = Canvas(buffer, 0, 0, buffer.width, buffer.height)

        /** A detached canvas over its own backing buffer — for measuring or scrolling content off-screen. */
        public fun offscreen(width: Int, height: Int): Canvas = of(ScreenBuffer(width, height))
    }
}
