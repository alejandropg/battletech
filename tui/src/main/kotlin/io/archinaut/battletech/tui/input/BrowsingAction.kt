package io.archinaut.battletech.tui.input

import io.archinaut.battletech.tactical.model.HexDirection

public sealed interface BrowsingAction : Action {
    public data class MoveCursor(val direction: HexDirection) : BrowsingAction

    public data object ConfirmPath : BrowsingAction

    public data class SelectFacing(val direction: HexDirection) : BrowsingAction

    public data object CycleMode : BrowsingAction

    public data object CycleUnit : BrowsingAction

    public data object Cancel : BrowsingAction
}
