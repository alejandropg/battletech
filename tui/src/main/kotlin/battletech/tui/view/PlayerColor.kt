package battletech.tui.view

import battletech.tactical.model.PlayerId
import battletech.tui.screen.BoardRole

/** Shared player→color mapping. */
internal fun playerColor(player: PlayerId): BoardRole = when (player) {
    PlayerId.PLAYER_1 -> BoardRole.PLAYER_1
    PlayerId.PLAYER_2 -> BoardRole.PLAYER_2
}

/** Shared player→short-label mapping (distinct from [battletech.tui.game.displayName]'s "Player 1"/"Player 2"). */
internal fun playerLabel(player: PlayerId): String = when (player) {
    PlayerId.PLAYER_1 -> "P1"
    PlayerId.PLAYER_2 -> "P2"
}
