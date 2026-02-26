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
    ) {
        val (arrowChar, arrowOffset) = facingArrow(facing)
        val arrowX = x + arrowOffset
        val southFacing = facing == HexDirection.SE || facing == HexDirection.S || facing == HexDirection.SW
        val initialRow = if (southFacing) 2 else 3
        val arrowRow = if (southFacing) 3 else 2

        buffer.set(x + 4, y + initialRow, Cell(initial.toString(), color, buffer.get(x + 4, y + initialRow).bg))
        buffer.set(arrowX, y + arrowRow, Cell(arrowChar, color, buffer.get(arrowX, y + arrowRow).bg))

        if (torsoFacing != null && torsoFacing != facing) {
            val (torsoChar, _) = torsoArrow(torsoFacing)
            val isClockwise = facing.rotateClockwise() == torsoFacing
            val torsoX = if (isClockwise) arrowX + 1 else arrowX - 1
            buffer.set(torsoX, y + arrowRow, Cell(torsoChar, color, buffer.get(torsoX, y + arrowRow).bg))
        }
    }

    // Leg facing arrows (larger arrows)
    private val ICON_FACING_N  = String(Character.toChars(0xF09C7))
    private val ICON_FACING_NE = String(Character.toChars(0xF09C5))
    private val ICON_FACING_SE = String(Character.toChars(0xF09B9))
    private val ICON_FACING_S  = String(Character.toChars(0xF09BF))
    private val ICON_FACING_SW = String(Character.toChars(0xF09B7))
    private val ICON_FACING_NW = String(Character.toChars(0xF09C3))

    // Torso twist arrows (smaller arrows per design doc)
    private val ICON_TORSO_N  = String(Character.toChars(0xF005D))
    private val ICON_TORSO_NE = String(Character.toChars(0xF005C))
    private val ICON_TORSO_SE = String(Character.toChars(0xF0043))
    private val ICON_TORSO_S  = String(Character.toChars(0xF0045))
    private val ICON_TORSO_SW = String(Character.toChars(0xF0042))
    private val ICON_TORSO_NW = String(Character.toChars(0xF005B))

    private fun facingArrow(direction: HexDirection): Pair<String, Int> = when (direction) {
        HexDirection.N  -> ICON_FACING_N  to 4
        HexDirection.NE -> ICON_FACING_NE to 5
        HexDirection.SE -> ICON_FACING_SE to 5
        HexDirection.S  -> ICON_FACING_S  to 4
        HexDirection.SW -> ICON_FACING_SW to 3
        HexDirection.NW -> ICON_FACING_NW to 3
    }

    private fun torsoArrow(direction: HexDirection): Pair<String, Int> = when (direction) {
        HexDirection.N  -> ICON_TORSO_N  to 4
        HexDirection.NE -> ICON_TORSO_NE to 5
        HexDirection.SE -> ICON_TORSO_SE to 5
        HexDirection.S  -> ICON_TORSO_S  to 4
        HexDirection.SW -> ICON_TORSO_SW to 3
        HexDirection.NW -> ICON_TORSO_NW to 3
    }
}
