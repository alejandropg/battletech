package battletech.tui.game.phase

import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage

internal data class Transition(
    val app: AppState,
    val flash: FlashMessage? = null,
)
