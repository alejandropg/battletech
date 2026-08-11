package battletech.tui.view

import battletech.tactical.model.PlayerId
import battletech.tui.screen.Color

/** Shared player→color mapping. */
internal fun playerColor(player: PlayerId): Color = when (player) {
    PlayerId.PLAYER_1 -> Color.PLAYER_1
    PlayerId.PLAYER_2 -> Color.PLAYER_2
}
