package io.archinaut.battletech.tui.hex

import io.archinaut.battletech.tactical.model.HexDirection
import io.archinaut.battletech.tui.icon.destroyedIcon
import io.archinaut.battletech.tui.icon.facingArrowIcon
import io.archinaut.battletech.tui.icon.torsoArrowIcon
import io.archinaut.battletech.tui.screen.BoardRole
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.color.ColorRole

public object UnitRenderer {

    public fun render(
        canvas: Canvas,
        x: Int,
        y: Int,
        id: String,
        facing: HexDirection,
        color: BoardRole,
        torsoFacing: HexDirection? = null,
        torsoColor: ColorRole = color,
        isDestroyed: Boolean = false,
    ) {
        val (arrowChar, arrowOffset) = facingArrowIcon(facing)
        val arrowX = x + arrowOffset
        val southFacing = facing == HexDirection.SE || facing == HexDirection.S || facing == HexDirection.SW
        val idRow = if (southFacing) 2 else 3
        val arrowRow = if (southFacing) 3 else 2

        // Torso arrow's own row/column, computed up front: both the id column choice and the
        // destroyed-marker placement below need to know where (if anywhere) it landed.
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
            canvas.setString(cellX, y + idRow, char.toString(), Style(fg = color))
        }

        if (isDestroyed) {
            // If a torso arrow occupies the id row (whether or not that shifted the id), the id
            // row is crowded, so the marker moves to the arrow row instead, avoiding whichever
            // column the facing arrow already claimed there.
            val markerRow = if (torsoInIdRow) arrowRow else idRow
            val markerX = if (torsoInIdRow) {
                if (arrowX == x + 4) x + 3 else x + 4
            } else {
                x + 3
            }
            canvas.setString(markerX, y + markerRow, destroyedIcon(), Style(fg = color))
        }

        canvas.setString(arrowX, y + arrowRow, arrowChar, Style(fg = color))

        if (torsoChar != null && torsoX != null && torsoRow != null) {
            canvas.setString(torsoX, y + torsoRow, torsoChar, Style(fg = torsoColor))
        }
    }

}
