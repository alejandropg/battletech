package io.archinaut.battletech.tui.hex

import io.archinaut.battletech.tactical.model.HexDirection
import io.archinaut.battletech.tui.icon.facingKey
import io.archinaut.battletech.tui.screen.BoardRole
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.widget.Widget

/**
 * The `qweasd` key that commits each of [facings] during facing selection. Drawn in
 * [BoardRole.BOARD_ACTIVE], like the cursor and the active path, since facing selection is itself
 * an active-cursor interaction.
 */
internal class FacingKeysView(private val facings: Set<HexDirection>) : Widget {

    override fun render(canvas: Canvas) {
        for (direction in facings) {
            val (x, y) = HexCells.facing(direction)
            canvas.setString(x, y, facingKey(direction), STYLE)
        }
    }

    private companion object {
        private val STYLE = Style(fg = BoardRole.BOARD_ACTIVE)
    }
}
