package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection

public sealed interface BrowsingAction {
    public data class MoveCursor(val direction: HexDirection) : BrowsingAction
    public data class ClickHex(val coords: HexCoordinates) : BrowsingAction
    public data object ConfirmPath : BrowsingAction
    public data class SelectFacing(val index: Int) : BrowsingAction
    public data object CycleMode : BrowsingAction
    public data object Cancel : BrowsingAction
}
