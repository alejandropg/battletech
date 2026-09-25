package io.archinaut.battletech.tui.input

import io.archinaut.battletech.tactical.model.HexDirection
import io.archinaut.tenter.input.InputAction

public sealed interface FacingAction : InputAction {
    public data class SelectFacing(val direction: HexDirection) : FacingAction {
        override val id: String get() = "selectFacing.${direction.name.lowercase()}"
    }

    public data object Cancel : FacingAction {
        override val id: String get() = "cancel"
    }

    public data object CycleUnit : FacingAction {
        override val id: String get() = "cycleUnit"
    }
}
