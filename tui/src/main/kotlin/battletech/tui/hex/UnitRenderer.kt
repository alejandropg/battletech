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
        id: String,
        facing: HexDirection,
        color: Color,
        torsoFacing: HexDirection? = null,
        isDestroyed: Boolean = false,
    ) {
        val (arrowChar, arrowOffset) = facingArrowIcon(facing)
        val arrowX = x + arrowOffset
        val southFacing = facing == HexDirection.SE || facing == HexDirection.S || facing == HexDirection.SW
        val idRow = if (southFacing) 2 else 3
        val arrowRow = if (southFacing) 3 else 2

        // Torso arrow's own row/column, computed up front: both the id column choice and the
        // skull placement below need to know where (if anywhere) it landed.
        val torsoChar: String?
        val torsoX: Int?
        val torsoRow: Int?
        if (torsoFacing != null && torsoFacing != facing) {
            val (twistChar, torsoOffset) = torsoArrowIcon(torsoFacing)
            val torsoSouth = torsoFacing == HexDirection.SE || torsoFacing == HexDirection.S || torsoFacing == HexDirection.SW
            torsoChar = twistChar
            torsoX = x + torsoOffset
            torsoRow = if (torsoSouth) 3 else 2
        } else {
            torsoChar = null
            torsoX = null
            torsoRow = null
        }
        val torsoInIdRow = torsoRow == idRow
        val idShiftedForTorso = torsoInIdRow && torsoX == x + 5

        val idBaseX = if (idShiftedForTorso) x + 3 else x + 4
        id.take(2).forEachIndexed { index, char ->
            val cellX = idBaseX + index
            buffer.set(cellX, y + idRow, Cell(char.toString(), Cell.Style(color, buffer.get(cellX, y + idRow).style.bg)))
        }

        if (isDestroyed) {
            // If a torso arrow occupies the id row (whether or not that shifted the id), the id
            // row is crowded, so the skull moves to the arrow row instead, avoiding whichever
            // column the facing arrow already claimed there.
            val skullRow = if (torsoInIdRow) arrowRow else idRow
            val skullX = if (torsoInIdRow) {
                if (arrowX == x + 4) x + 3 else x + 4
            } else {
                x + 3
            }
            buffer.set(skullX, y + skullRow, Cell(destroyedIcon(), Cell.Style(color, buffer.get(skullX, y + skullRow).style.bg)))
        }

        buffer.set(arrowX, y + arrowRow, Cell(arrowChar, Cell.Style(color, buffer.get(arrowX, y + arrowRow).style.bg)))

        if (torsoChar != null && torsoX != null && torsoRow != null) {
            buffer.set(torsoX, y + torsoRow, Cell(torsoChar, Cell.Style(color, buffer.get(torsoX, y + torsoRow).style.bg)))
        }
    }

}
