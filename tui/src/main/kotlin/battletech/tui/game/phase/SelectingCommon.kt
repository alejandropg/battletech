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
 * Shared input handler for every "select a unit" idle state
 * ([MovementPhase.SelectingUnit], [AttackPhase.SelectingAttacker], and
 * [PhysicalAttackPhase.SelectingAttacker]). All three share the same
 * interaction vocabulary:
 *
 * - arrow/wasd/qe → move the cursor,
 * - Enter / click → select the unit under the cursor (subject to ownership and
 *   [selectGuard]) and enter the phase's sub-mode via [enterFor],
 * - Tab → cycle to the next unit in [selectableUnits] and *also* enter the
 *   sub-mode for it, so Tab and Enter land in the same place,
 * - 'c' → run [onCommit] (a no-op by default, as in movement).
 *
 * Returns null when the event maps to no idle action, matching [mapIdleInput].
 */
internal fun handleUnitSelection(
    event: InputEvent,
    app: AppState,
    activePlayer: () -> PlayerId,
    selectableUnits: () -> List<CombatUnit>,
    selectGuard: (CombatUnit) -> FlashMessage? = { null },
    onCommit: (AppState) -> Transition = { Transition(it) },
    enterFor: (CombatUnit, AppState) -> Transition,
): Transition? {
    val action = mapIdleInput(event) ?: return null
    // [activePlayer] and [selectableUnits] are evaluated lazily: cursor moves
    // and commits must not touch turn-state fields that may be absent (e.g.
    // TurnState.NULL) when no unit selection is actually happening.
    return when (action) {
        is IdleAction.MoveCursor -> handleCursorMove(app, action)
        is IdleAction.ClickHex ->
            selectUnitAt(app.copy(cursor = action.coords), activePlayer(), selectGuard, enterFor)
        is IdleAction.SelectUnit -> selectUnitAt(app, activePlayer(), selectGuard, enterFor)
        is IdleAction.CycleUnit -> cycleAndEnter(app, selectableUnits(), enterFor)
        is IdleAction.CommitDeclarations -> onCommit(app)
    }
}

private fun selectUnitAt(
    app: AppState,
    activePlayer: PlayerId,
    selectGuard: (CombatUnit) -> FlashMessage?,
    enterFor: (CombatUnit, AppState) -> Transition,
): Transition = selectOwnUnit(app, activePlayer, selectGuard) { unit -> enterFor(unit, app) }

/**
 * Advance the cursor to the next unit in [selectableUnits] and enter the
 * phase's sub-mode for it via [enterFor].
 *
 * - Empty list → `Transition(app)` (no-op).
 * - Cursor not on any listed unit → enters the first unit.
 * - Otherwise → enters `selectableUnits[(idx + 1) % size]`.
 *
 * The current unit is identified by the unit under the cursor, which is always
 * correct in an idle selecting state. The in-sub-mode cyclers
 * ([cycleToNextUnit], `AttackPhase.Declaring.nextAttacker`) stay separate:
 * there the cursor may sit on a destination/target hex rather than the active
 * unit, and they also carry per-unit draft state.
 */
private fun cycleAndEnter(
    app: AppState,
    selectableUnits: List<CombatUnit>,
    enterFor: (CombatUnit, AppState) -> Transition,
): Transition {
    if (selectableUnits.isEmpty()) return Transition(app)
    val currentId = app.gameState.unitAt(app.cursor)?.id
    val idx = selectableUnits.indexOfFirst { it.id == currentId }
    val next = selectableUnits[if (idx == -1) 0 else (idx + 1) % selectableUnits.size]
    return enterFor(next, app.copy(cursor = next.position))
}
