package io.archinaut.battletech.tui.game.phase

import io.archinaut.battletech.tui.game.AppState
import io.archinaut.tenter.view.FlashMessage

internal data class Transition(
    val app: AppState,
    val flash: FlashMessage? = null,
)
