package tenter.screen

public class ScreenBuffer(
    public val width: Int,
    public val height: Int,
) {
    private val cells: Array<Array<Cell>> = Array(height) { Array(width) { Cell.EMPTY } }

    /** The reveal rect last marked via [Canvas.markReveal], in this buffer's own absolute coords. */
    internal var reveal: RevealRect? = null

    public fun get(x: Int, y: Int): Cell {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw IndexOutOfBoundsException("($x, $y) out of bounds for ${width}x$height buffer")
        }
        return cells[y][x]
    }

    public fun set(x: Int, y: Int, cell: Cell) {
        if (x < 0 || x >= width || y < 0 || y >= height) return
        cells[y][x] = cell
    }

    /** Raw row-slice copy backing [Canvas.blit]. Callers are responsible for clipping. */
    internal fun copyCellsFrom(src: ScreenBuffer, srcRow: Int, srcCol: Int, destRow: Int, destCol: Int, count: Int) {
        System.arraycopy(src.cells[srcRow], srcCol, cells[destRow], destCol, count)
    }
}
