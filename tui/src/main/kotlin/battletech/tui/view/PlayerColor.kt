package battletech.tui.view

import battletech.tactical.model.PlayerId
import battletech.tui.screen.Color

/** Shared player→color mapping. Null (unknown owner) renders white. */
internal fun playerColor(player: PlayerId?): Color = when (player) {
    PlayerId.PLAYER_1 -> Color.BLUE
    PlayerId.PLAYER_2 -> Color.MAGENTA
    null -> Color.WHITE
}
