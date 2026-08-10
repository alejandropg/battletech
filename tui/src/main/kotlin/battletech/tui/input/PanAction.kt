package battletech.tui.input

/** A manual board-pan action — see [InputMapper.mapPanEvent]. */
internal sealed interface PanAction {
    /** Shift the board viewport by ([dx], [dy]) characters. */
    data class Pan(val dx: Int, val dy: Int) : PanAction

    /** Snap the viewport back to center on the cursor. */
    data object Recenter : PanAction
}
