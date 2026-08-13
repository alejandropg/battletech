package battletech.tui.game.phase

import battletech.tui.game.AppState
import tenter.panel.FlashMessage

internal data class Transition(
    val app: AppState,
    val flash: FlashMessage? = null,
)
