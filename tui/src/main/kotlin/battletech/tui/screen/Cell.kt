package battletech.tui.screen

public enum class Color {
    DEFAULT,
    BLACK,
    RED,
    GREEN,
    BLUE,
    YELLOW,
    CYAN,
    WHITE,
    DARK_GREEN,
    BROWN,
    BRIGHT_YELLOW,
}

public data class Cell(
    val char: Char = ' ',
    val fg: Color = Color.DEFAULT,
    val bg: Color = Color.DEFAULT,
)

public data class CellChange(
    val x: Int,
    val y: Int,
    val cell: Cell,
)
