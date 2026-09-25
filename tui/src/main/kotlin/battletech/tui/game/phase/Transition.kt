package battletech.tui.game.phase

import battletech.tui.game.AppState
import io.archinaut.tenter.view.FlashMessage

internal data class Transition(
    val app: AppState,
    val flash: FlashMessage? = null,
)
