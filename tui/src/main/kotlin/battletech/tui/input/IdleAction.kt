package battletech.tui.input

import battletech.tactical.model.HexDirection
import tenter.input.InputAction

public sealed interface IdleAction : InputAction {
    public data class MoveCursor(val direction: HexDirection) : IdleAction {
        override val id: String get() = "moveCursor.${direction.name.lowercase()}"
    }

    public data object SelectUnit : IdleAction {
        override val id: String get() = "selectUnit"
    }

    public data object CycleUnit : IdleAction {
        override val id: String get() = "cycleUnit"
    }

    public data object CommitDeclarations : IdleAction {
        override val id: String get() = "commitDeclarations"
    }
}
