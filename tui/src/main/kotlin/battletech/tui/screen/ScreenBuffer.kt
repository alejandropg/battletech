package battletech.tui.screen

public class ScreenBuffer(
    public val width: Int,
    public val height: Int,
) {
    private val cells: Array<Array<Cell>> = Array(height) { Array(width) { Cell() } }

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

    public fun writeString(
        x: Int,
        y: Int,
        text: String,
        fg: Color = Color.DEFAULT,
        bg: Color = Color.DEFAULT,
    ) {
        for ((i, char) in text.withIndex()) {
            val cx = x + i
            if (cx >= width) break
            set(cx, y, Cell(char, fg, bg))
        }
    }

    public fun diff(other: ScreenBuffer): List<CellChange> {
        val changes = mutableListOf<CellChange>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val otherCell = other.cells[y][x]
                if (cells[y][x] != otherCell) {
                    changes.add(CellChange(x, y, otherCell))
                }
            }
        }
        return changes
    }
}
