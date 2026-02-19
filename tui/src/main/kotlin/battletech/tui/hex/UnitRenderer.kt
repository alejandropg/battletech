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
        // Unit initial at hex center (row 3, col 4)
        buffer.set(x + 4, y + 3, Cell(initial, color, buffer.get(x + 4, y + 3).bg))

        // Facing arrow in row 2
        val (arrowChar, arrowOffset) = facingArrow(facing)
        val arrowX = x + arrowOffset
        val arrowY = y + 2
        buffer.set(arrowX, arrowY, Cell(arrowChar, color, buffer.get(arrowX, arrowY).bg))
    }

    private fun facingArrow(direction: HexDirection): Pair<Char, Int> = when (direction) {
        HexDirection.N -> '^' to 4
        HexDirection.NE -> '/' to 5
        HexDirection.SE -> '\\' to 5
        HexDirection.S -> 'v' to 4
        HexDirection.SW -> '/' to 3
        HexDirection.NW -> '\\' to 3
    }
}
