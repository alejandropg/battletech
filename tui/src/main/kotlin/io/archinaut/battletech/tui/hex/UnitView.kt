package io.archinaut.battletech.tui.hex

import io.archinaut.battletech.tactical.model.HexDirection
import io.archinaut.battletech.tui.icon.destroyedIcon
import io.archinaut.battletech.tui.icon.facingArrowIcon
import io.archinaut.battletech.tui.icon.torsoArrowIcon
import io.archinaut.battletech.tui.screen.BoardRole
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.color.ColorRole
import io.archinaut.tenter.widget.Widget

/**
 * A unit inside its hex's [HexGeometry.HEX_WIDTH] x [HexGeometry.HEX_HEIGHT] area: its two-character
 * [id] and [facing] arrow in [color], a [torsoFacing] arrow in [torsoColor] when the torso is
 * twisted, and a destroyed marker when [isDestroyed].
 */
internal class UnitView(
    private val id: String,
    private val facing: HexDirection,
    private val color: BoardRole,
    private val torsoFacing: HexDirection? = null,
    private val torsoColor: ColorRole = color,
    private val isDestroyed: Boolean = false,
) : Widget {

    override fun render(canvas: Canvas) {
        val (arrowChar, arrowOffset) = facingArrowIcon(facing)
        val arrowX = arrowOffset
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
            torsoX = torsoOffset
            torsoRow = if (torsoSouth) 3 else 2
        } else {
            torsoChar = null
            torsoX = null
            torsoRow = null
        }
        val torsoInIdRow = torsoRow == idRow
        val idShiftedForTorso = torsoInIdRow && torsoX == 5

        val idBaseX = if (idShiftedForTorso) 3 else 4
        id.take(2).forEachIndexed { index, char ->
            val cellX = idBaseX + index
            canvas.setString(cellX, idRow, char.toString(), Style(fg = color))
        }

        if (isDestroyed) {
            // If a torso arrow occupies the id row (whether or not that shifted the id), the id
            // row is crowded, so the marker moves to the arrow row instead, avoiding whichever
            // column the facing arrow already claimed there.
            val markerRow = if (torsoInIdRow) arrowRow else idRow
            val markerX = if (torsoInIdRow) {
                if (arrowX == 4) 3 else 4
            } else {
                3
            }
            canvas.setString(markerX, markerRow, destroyedIcon(), Style(fg = color))
        }

        canvas.setString(arrowX, arrowRow, arrowChar, Style(fg = color))

        if (torsoChar != null && torsoX != null && torsoRow != null) {
            canvas.setString(torsoX, torsoRow, torsoChar, Style(fg = torsoColor))
        }
    }
}
