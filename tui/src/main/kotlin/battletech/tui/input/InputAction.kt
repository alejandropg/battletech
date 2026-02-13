package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection

public sealed interface InputAction {
    public data class MoveCursor(val direction: HexDirection) : InputAction
    public data object Confirm : InputAction
    public data object Cancel : InputAction
    public data object CycleUnit : InputAction
    public data class SelectAction(val index: Int) : InputAction
    public data object Quit : InputAction
    public data class ClickHex(val coords: HexCoordinates) : InputAction
    public data class ScrollBoard(val dx: Int, val dy: Int) : InputAction
}
