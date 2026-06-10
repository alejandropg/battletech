package battletech.tui.game.phase

import battletech.tactical.model.PlayerId
import battletech.tactical.unit.CombatUnit
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.moveCursor
import battletech.tui.input.IdleAction
import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

/** Board-origin constants used by all idle-selecting states. */
internal const val BOARD_ORIGIN_X = 2
internal const val BOARD_ORIGIN_Y = 2

/**
 * Map an [InputEvent] to an [IdleAction], or return null if the event is not
 * handled in idle/selecting states. This is the shared input-mapping block
 * used by [MovementPhase.SelectingUnit], [AttackPhase.SelectingAttacker], and
 * [PhysicalAttackPhase.SelectingAttacker].
 */
internal fun mapIdleInput(event: InputEvent): IdleAction? = when (event) {
    is KeyboardEvent -> InputMapper.mapIdleEvent(event)
    is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = BOARD_ORIGIN_X, boardY = BOARD_ORIGIN_Y)
        ?.let { IdleAction.ClickHex(it) }
}

/**
 * Handle a [IdleAction.MoveCursor] action: move the cursor one step in the
 * given direction (clamped to map boundaries) and return the resulting
 * [Transition].
 */
internal fun handleCursorMove(app: AppState, action: IdleAction.MoveCursor): Transition =
    Transition(app.copy(cursor = moveCursor(app.cursor, action.direction, app.gameState.map)))

/**
 * Try to select the unit at the cursor as the active player's unit.
 *
 * - No unit at cursor → `Transition(app)` (no-op).
 * - Unit owned by someone other than [activePlayer] → `Transition(app, FlashMessage("Not your unit"))`.
 * - [extraGuard] returns a non-null [FlashMessage] → `Transition(app, that message)`.
 * - Otherwise → `onSelect(unit)`.
 *
 * The [extraGuard] is evaluated only after the ownership check passes, so
 * ownership always takes priority over phase-specific guards.
 */
internal fun selectOwnUnit(
    app: AppState,
    activePlayer: PlayerId,
    extraGuard: (CombatUnit) -> FlashMessage? = { null },
    onSelect: (CombatUnit) -> Transition,
): Transition {
    val unit = app.gameState.unitAt(app.cursor) ?: return Transition(app)
    if (unit.owner != activePlayer) return Transition(app, FlashMessage("Not your unit"))
    extraGuard(unit)?.let { return Transition(app, it) }
    return onSelect(unit)
}

/**
 * Advance the cursor to the next unit in [units] by position, cycling
 * through the list.
 *
 * - Empty list → `Transition(app)`.
 * - Cursor not on any listed unit (index == -1) → moves to `units[0]`.
 * - Otherwise → moves to `units[(idx + 1) % units.size]`.
 *
 * This covers the identical cycle logic in [AttackPhase.SelectingAttacker]
 * and [PhysicalAttackPhase.SelectingAttacker]. The movement-phase cycle
 * ([cycleToNextUnit]) is intentionally kept separate because it also enters
 * [MovementPhase.Browsing] and uses unit-id lookup rather than position.
 */
internal fun cycleSelectable(app: AppState, units: List<CombatUnit>): Transition {
    if (units.isEmpty()) return Transition(app)
    val idx = units.indexOfFirst { it.position == app.cursor }
    return Transition(app.copy(cursor = units[(idx + 1) % units.size].position))
}
