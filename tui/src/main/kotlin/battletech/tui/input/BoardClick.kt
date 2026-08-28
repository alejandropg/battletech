package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import tenter.input.InputAction

/**
 * A left-click resolved to a board hex. Produced by `runLoop` (which owns the board origin and the
 * board's settled scroll offset), never by the keymap — the mouse is deliberately not bindable.
 * Replaces the three per-phase click actions that said the same thing in three types.
 */
internal data class BoardClick(val coords: HexCoordinates) : InputAction {
    override val id: String get() = "boardClick"
}
