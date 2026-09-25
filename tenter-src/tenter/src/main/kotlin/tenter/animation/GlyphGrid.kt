package tenter.animation

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.text.CellWidth
import tenter.view.View

/**
 * A mutable grid of single-cell terminal glyphs with priority compositing and per-glyph styles.
 * Characters supplied to [put] and [set] must occupy exactly one terminal cell; surrogate
 * characters and zero- or two-cell characters are rejected for in-bounds writes. Out-of-bounds
 * writes are silently dropped.
 *
 * [priority] and [style] are captured for this grid's lifetime, and callers must supply functions
 * whose results remain stable for that lifetime. Build a frame by mutating the grid, then leave the
 * returned grid unchanged so that its rendering remains stable.
 */
public class GlyphGrid(
    public val size: AnimationSize,
    private val priority: (Char) -> Int,
    private val style: (Char) -> Cell.Style,
) : View {
    public val width: Int = size.width
    public val height: Int = size.height

    private val cells: Array<CharArray> = Array(height) { CharArray(width) { ' ' } }

    /** The glyph at ([x], [y]). Throws if the coordinates are outside this grid. */
    public fun get(x: Int, y: Int): Char = cells[y][x]

    /** The style supplied for the glyph at ([x], [y]). */
    public fun styleAt(x: Int, y: Int): Cell.Style = style(get(x, y))

    /**
     * Writes [char] unless a strictly higher-priority glyph already occupies the cell. Equal
     * priority writes replace the old glyph.
     */
    public fun put(x: Int, y: Int, char: Char) {
        if (!inBounds(x, y)) return
        validate(char)
        if (priority(char) >= priority(cells[y][x])) cells[y][x] = char
    }

    /** Writes [char] regardless of the current cell priority. */
    public fun set(x: Int, y: Int, char: Char) {
        if (!inBounds(x, y)) return
        validate(char)
        cells[y][x] = char
    }

    /** Paints every cell, including styled spaces, into the clipped destination canvas. */
    public override fun draw(canvas: Canvas) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                canvas.set(x, y, Cell(get(x, y).toString(), styleAt(x, y)))
            }
        }
    }

    private fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    private fun validate(char: Char) {
        require(!char.isSurrogate()) { "animation glyph must not be a surrogate: U+%04X".format(char.code) }
        require(CellWidth.of(char.code) == 1) {
            "animation glyph must occupy one terminal cell: U+%04X".format(char.code)
        }
    }
}
