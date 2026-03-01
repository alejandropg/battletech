package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection

public sealed interface IdleAction {
    public data class MoveCursor(val direction: HexDirection) : IdleAction
    public data class ClickHex(val coords: HexCoordinates) : IdleAction
    public data object SelectUnit : IdleAction
    public data object CycleUnit : IdleAction
    public data object CommitDeclarations : IdleAction
}
