package battletech.tui.hex

import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import battletech.tactical.model.HexDirection

public object UnitRenderer {

    public fun render(
        buffer: ScreenBuffer,
        x: Int,
        y: Int,
        initial: Char,
        facing: HexDirection,
        color: Color,
    ) {
        // Unit initial at hex center (row 2, col 3)
        buffer.set(x + 3, y + 2, Cell(initial, color, buffer.get(x + 3, y + 2).bg))

        // Facing arrow in row 1
        val (arrowChar, arrowOffset) = facingArrow(facing)
        val arrowX = x + arrowOffset
        val arrowY = y + 1
        buffer.set(arrowX, arrowY, Cell(arrowChar, color, buffer.get(arrowX, arrowY).bg))
    }

    private fun facingArrow(direction: HexDirection): Pair<Char, Int> = when (direction) {
        HexDirection.N -> '^' to 3
        HexDirection.NE -> '/' to 4
        HexDirection.SE -> '\\' to 4
        HexDirection.S -> 'v' to 3
        HexDirection.SW -> '/' to 2
        HexDirection.NW -> '\\' to 2
    }
}
