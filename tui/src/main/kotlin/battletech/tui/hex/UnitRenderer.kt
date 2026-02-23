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
        buffer.set(x + 4, y + 3, Cell(initial.toString(), color, buffer.get(x + 4, y + 3).bg))

        // Facing arrow in row 2
        val (arrowChar, arrowOffset) = facingArrow(facing)
        val arrowX = x + arrowOffset
        val arrowY = y + 2
        buffer.set(arrowX, arrowY, Cell(arrowChar, color, buffer.get(arrowX, arrowY).bg))
    }

    private val ICON_FACING_N  = String(Character.toChars(0xF09C7))
    private val ICON_FACING_NE = String(Character.toChars(0xF09C5))
    private val ICON_FACING_SE = String(Character.toChars(0xF09B9))
    private val ICON_FACING_S  = String(Character.toChars(0xF09BF))
    private val ICON_FACING_SW = String(Character.toChars(0xF09B7))
    private val ICON_FACING_NW = String(Character.toChars(0xF09C3))

    private fun facingArrow(direction: HexDirection): Pair<String, Int> = when (direction) {
        HexDirection.N  -> ICON_FACING_N  to 4
        HexDirection.NE -> ICON_FACING_NE to 5
        HexDirection.SE -> ICON_FACING_SE to 5
        HexDirection.S  -> ICON_FACING_S  to 4
        HexDirection.SW -> ICON_FACING_SW to 3
        HexDirection.NW -> ICON_FACING_NW to 3
    }
}
