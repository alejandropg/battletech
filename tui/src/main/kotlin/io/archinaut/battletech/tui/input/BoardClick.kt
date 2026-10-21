package io.archinaut.battletech.tui.input

import io.archinaut.battletech.tactical.model.HexCoordinates

/**
 * A left-click resolved to a board hex. Produced by `runLoop` (which owns the board origin and the
 * board's settled scroll offset), never by the keymap — the mouse is deliberately not bindable.
 * Replaces the three per-phase click actions that said the same thing in three types.
 */
internal data class BoardClick(val coords: HexCoordinates) : Action
