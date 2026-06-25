package battletech.tui.hex

import battletech.tactical.model.HexDirection
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public object UnitRenderer {

    public fun render(
        buffer: ScreenBuffer,
        x: Int,
        y: Int,
        initial: Char,
        facing: HexDirection,
        color: Color,
        torsoFacing: HexDirection? = null,
        isDestroyed: Boolean = false,
    ) {
        val (arrowChar, arrowOffset) = facingArrowIcon(facing)
        val arrowX = x + arrowOffset
        val southFacing = facing == HexDirection.SE || facing == HexDirection.S || facing == HexDirection.SW
        val initialRow = if (southFacing) 2 else 3
        val arrowRow = if (southFacing) 3 else 2

        buffer.set(x + 4, y + initialRow, Cell(initial.toString(), color, buffer.get(x + 4, y + initialRow).bg))
        if (isDestroyed) {
            buffer.set(x + 5, y + initialRow, Cell(destroyedIcon(), color, buffer.get(x + 5, y + initialRow).bg))
        }
        buffer.set(arrowX, y + arrowRow, Cell(arrowChar, color, buffer.get(arrowX, y + arrowRow).bg))

        if (torsoFacing != null && torsoFacing != facing) {
            val (torsoChar, torsoOffset) = torsoArrowIcon(torsoFacing)
            val torsoSouth = torsoFacing == HexDirection.SE || torsoFacing == HexDirection.S || torsoFacing == HexDirection.SW
            val torsoX = x + torsoOffset
            val torsoRow = if (torsoSouth) 3 else 2
            buffer.set(torsoX, y + torsoRow, Cell(torsoChar, color, buffer.get(torsoX, y + torsoRow).bg))
        }
    }

}
