package tenter.screen

import tenter.text.textClusters

public class ScreenBuffer(
    public val width: Int,
    public val height: Int,
) {
    private val cells: Array<Array<StoredCell>>

    init {
        require(width >= 0) { "width must not be negative: $width" }
        require(height >= 0) { "height must not be negative: $height" }
        cells = Array(height) { Array(width) { StoredCell(Cell.EMPTY, width = 1, continuation = false) } }
    }

    /** The reveal rect last marked via [Canvas.markReveal], in this buffer's own absolute coords. */
    internal var reveal: RevealRect? = null

    public fun get(x: Int, y: Int): Cell {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw IndexOutOfBoundsException("($x, $y) out of bounds for ${width}x$height buffer")
        }
        return cells[y][x].asPublicCell()
    }

    public fun set(x: Int, y: Int, cell: Cell) {
        if (x < 0 || x >= width || y < 0 || y >= height) return
        writeCell(x, y, cell, 0, 0, width, height)
    }

    internal fun writeCell(
        x: Int,
        y: Int,
        cell: Cell,
        clipLeft: Int,
        clipTop: Int,
        clipRight: Int,
        clipBottom: Int,
    ) {
        val glyph = cell.toStoredGlyph()
        if (y !in clipTop until clipBottom) return
        if (glyph.width == 2 && x < clipLeft && x + 1 >= clipLeft) {
            writeBlank(clipLeft, y, glyph.cell.style, clipLeft, clipTop, clipRight, clipBottom)
            return
        }
        if (x !in clipLeft until clipRight) return

        if (glyph.width == 2 && x + 1 >= clipRight) {
            writeBlank(x, y, glyph.cell.style, clipLeft, clipTop, clipRight, clipBottom)
            return
        }

        val targetEnd = x + glyph.width
        if (targetEnd > clipRight || targetEnd > width) return
        if (!canReplace(x, y, targetEnd, clipLeft, clipTop, clipRight, clipBottom)) return

        clearGlyphAt(x, y)
        if (glyph.width == 2) clearGlyphAt(x + 1, y)
        cells[y][x] = glyph
        if (glyph.width == 2) {
            cells[y][x + 1] = StoredCell(glyph.cell, width = 2, continuation = true)
        }
    }

    internal fun snapshot(): ScreenBuffer {
        val copy = ScreenBuffer(width, height)
        for (y in 0 until height) {
            copy.cells[y] = cells[y].copyOf()
        }
        return copy
    }

    internal fun snapshotCells(): Array<Array<StoredCell>> = Array(height) { y -> cells[y].copyOf() }

    internal fun glyphStart(x: Int, y: Int): Int = if (cells[y][x].continuation) x - 1 else x

    internal fun glyphEndExclusive(x: Int, y: Int): Int {
        val cell = cells[y][x]
        return if (!cell.continuation && cell.width == 2) x + 2 else glyphStart(x, y) + 1
    }

    private fun canReplace(
        x: Int,
        y: Int,
        targetEnd: Int,
        clipLeft: Int,
        clipTop: Int,
        clipRight: Int,
        clipBottom: Int,
    ): Boolean {
        if (y !in clipTop until clipBottom) return false
        val leads = (x until targetEnd).map { glyphStart(it, y) }.distinct()
        return leads.all { lead ->
            val stored = cells[y][lead]
            lead >= clipLeft && lead + stored.width <= clipRight && lead + stored.width <= width
        }
    }

    private fun clearGlyphAt(x: Int, y: Int) {
        val lead = glyphStart(x, y)
        val stored = cells[y][lead]
        cells[y][lead] = StoredCell(Cell(" ", stored.cell.style), width = 1, continuation = false)
        if (stored.width == 2 && lead + 1 < width) {
            cells[y][lead + 1] = StoredCell(Cell(" ", stored.cell.style), width = 1, continuation = false)
        }
    }

    private fun writeBlank(
        x: Int,
        y: Int,
        style: Cell.Style,
        clipLeft: Int,
        clipTop: Int,
        clipRight: Int,
        clipBottom: Int,
    ) {
        if (!canReplace(x, y, x + 1, clipLeft, clipTop, clipRight, clipBottom)) return
        clearGlyphAt(x, y)
        cells[y][x] = StoredCell(Cell(" ", style), width = 1, continuation = false)
    }

    private fun Cell.toStoredGlyph(): StoredCell {
        if (char.isEmpty()) return StoredCell(Cell(" ", style), width = 1, continuation = false)
        val clusters = textClusters(char).toList()
        require(clusters.size == 1 && clusters.single().width > 0) {
            "Cell must contain exactly one drawable grapheme cluster: ${char.toDebugString()}"
        }
        val cluster = clusters.single()
        return StoredCell(Cell(cluster.drawableText, style), cluster.width, continuation = false)
    }
}

internal data class StoredCell(
    internal val cell: Cell,
    internal val width: Int,
    internal val continuation: Boolean,
) {
    internal fun asPublicCell(): Cell = if (continuation) Cell("", cell.style) else cell
}

private fun String.toDebugString(): String = replace("\u001B", "<ESC>")
