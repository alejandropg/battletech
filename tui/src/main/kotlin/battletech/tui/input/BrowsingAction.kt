package battletech.tui.input

import battletech.tactical.model.HexDirection
import tenter.input.InputAction

public sealed interface BrowsingAction : InputAction {
    public data class MoveCursor(val direction: HexDirection) : BrowsingAction {
        override val id: String get() = "moveCursor.${direction.name.lowercase()}"
    }

    public data object ConfirmPath : BrowsingAction {
        override val id: String get() = "confirmPath"
    }

    public data class SelectFacing(val direction: HexDirection) : BrowsingAction {
        override val id: String get() = "selectFacing.${direction.name.lowercase()}"
    }

    public data object CycleMode : BrowsingAction {
        override val id: String get() = "cycleMode"
    }

    public data object CycleUnit : BrowsingAction {
        override val id: String get() = "cycleUnit"
    }

    public data object Cancel : BrowsingAction {
        override val id: String get() = "cancel"
    }
}
