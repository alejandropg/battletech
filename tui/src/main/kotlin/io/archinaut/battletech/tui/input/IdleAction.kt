package io.archinaut.battletech.tui.input

import io.archinaut.battletech.tactical.model.HexDirection

public sealed interface IdleAction : Action {
    public data class MoveCursor(val direction: HexDirection) : IdleAction

    public data object SelectUnit : IdleAction

    public data object CycleUnit : IdleAction

    public data object CommitDeclarations : IdleAction
}
