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
            set(cx, y, Cell(text.substring(i, i + charCount), fg, bg))
            if (w == 2 && cx + 1 < width) {
                set(cx + 1, y, Cell("", fg, bg))
            }
            cx += w
            i += charCount
        }
    }

    public fun drawBox(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        title: String = "",
        index: Int? = null,
        borderColor: Color = Color.GREEN,
        titleColor: Color = Color.BRIGHT_YELLOW,
    ) {
        if (width < 2 || height < 2) return

        set(x, y, Cell("╭", borderColor))
        set(x + width - 1, y, Cell("╮", borderColor))
        set(x, y + height - 1, Cell("╰", borderColor))
        set(x + width - 1, y + height - 1, Cell("╯", borderColor))

        for (i in 1 until width - 1) {
            set(x + i, y, Cell("─", borderColor))
            set(x + i, y + height - 1, Cell("─", borderColor))
        }

        for (i in 1 until height - 1) {
            set(x, y + i, Cell("│", borderColor))
            set(x + width - 1, y + i, Cell("│", borderColor))
        }

        if (title.isNotEmpty()) {
            if (index != null && width > title.length + 8) {
                writeString(x + 2, y, "[$index] $title", titleColor)
                set(x + 6 + title.length, y, Cell(" ", borderColor))
            } else if (index == null && width > title.length + 6) {
                set(x + 3, y, Cell(" ", borderColor))
                writeString(x + 4, y, title, titleColor)
                set(x + 4 + title.length, y, Cell(" ", borderColor))
            }
        }
    }

    public fun blit(src: ScreenBuffer, srcX: Int, srcY: Int, destX: Int, destY: Int, width: Int, height: Int) {
        val startCol = maxOf(0, -srcX, -destX)
        val endCol = minOf(width, src.width - srcX, this.width - destX)
        val count = endCol - startCol
        if (count <= 0) return
        for (row in 0 until height) {
            val sy = srcY + row
            if (sy < 0 || sy >= src.height) continue
            val dy = destY + row
            if (dy < 0 || dy >= this.height) continue
            System.arraycopy(
                src.cells[sy], srcX + startCol,
                cells[dy], destX + startCol,
                count,
            )
        }
    }

}
