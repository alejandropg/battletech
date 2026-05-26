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

        if (title.isNotEmpty() && width > title.length + 6) {
            set(x + 3, y, Cell(" ", borderColor))
            writeString(x + 4, y, title, titleColor)
            set(x + 4 + title.length, y, Cell(" ", borderColor))
        }
    }

}
