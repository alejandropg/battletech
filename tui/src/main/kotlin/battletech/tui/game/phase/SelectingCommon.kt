package battletech.tui.game.phase

import battletech.tactical.model.PlayerId
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommandResult
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.VisibleUnit
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
 * A short flash for a rejected command, or null if [result] was accepted.
 * Shared by the three `submitCommand` call sites (movement, weapon-attack
 * impulse, physical-attack impulse) so a rejection is never silently
 * swallowed — most visibly needed for the remote-play disconnect freeze
 * ([CommandRejection.OpponentUnavailable]), but useful for any rejection.
 */
internal fun rejectionFlash(result: CommandResult): FlashMessage? = when (result) {
    is CommandResult.Accepted -> null
    is CommandResult.Rejected -> when (result.reason) {
        is CommandRejection.OpponentUnavailable -> FlashMessage("Opponent not connected")
        else -> FlashMessage("Command rejected")
    }
    // Only reachable over the network seam (GameServer catches UnknownUnitException) — the
    // host-embedded UI path never produces this, it would crash instead. See CommandResult.ProtocolError.
    is CommandResult.ProtocolError -> FlashMessage("Command error")
}

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
    Transition(app.copy(cursor = moveCursor(app.cursor, action.direction, app.visibleState.map)))

/**
 * Guard against acting outside a seat this process drives.
 *
 * Returns `Transition(app, FlashMessage("Waiting for opponent"))` when [activePlayer] is not one
 * of [AppState.seats], else `null` so the caller can fall through to the real handling via `?:`.
 * Hot-seat never flashes — not because anything here special-cases hot-seat, but because
 * [AppState.seats] holds both players there, so [activePlayer] is always a member.
 *
 * [activePlayer] is now evaluated unconditionally (there is no nullable "local player" left to
 * gate it on). Every `handle()` that reaches this guard must therefore already know its own
 * turn-state field is seeded before calling in — see [MovementPhase.SelectingUnit]'s
 * `turnState.movement.isComplete` guard and [AttackPhase.SelectingAttacker]'s /
 * [PhysicalAttackPhase.SelectingAttacker]'s `turnState.attack.isComplete` guard, both of which
 * short-circuit to cursor-only handling — never reaching this function, let alone [activePlayer]
 * — while the impulse sequence is unseeded (e.g. fresh [TurnState.NULL]).
 *
 * This is the single source of truth for the seat check: [selectOwnUnit] calls it for the
 * Enter/click select path, and [handleUnitSelection] calls it directly (before [selectOwnUnit]
 * would otherwise run) for Tab and commit, which never reach [selectOwnUnit].
 */
private fun localTurnGuard(app: AppState, activePlayer: () -> PlayerId): Transition? =
    if (activePlayer() in app.seats) null else Transition(app, FlashMessage("Waiting for opponent"))

/**
 * Try to select the unit at the cursor as the active player's unit.
 *
 * - No unit at cursor → `Transition(app)` (no-op).
 * - [activePlayer] is not a seat this process drives (remote play, not this
 *   client's turn) → `Transition(app, FlashMessage("Waiting for opponent"))`
 *   (see [localTurnGuard]; kept here too as defense in depth for any direct
 *   caller of this function, though [handleUnitSelection] now also checks
 *   this before calling in).
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
    val visible = app.visibleState.unitAt(app.cursor) ?: return Transition(app)
    localTurnGuard(app) { activePlayer }?.let { return it }
    if (visible.owner != activePlayer) return Transition(app, FlashMessage("Not your unit"))
    val unit = app.ownUnit(visible.id)
    extraGuard(unit)?.let { return Transition(app, it) }
    return onSelect(unit)
}

/**
 * Shared input handler for every "select a unit" idle state
 * ([MovementPhase.SelectingUnit], [AttackPhase.SelectingAttacker], and
 * [PhysicalAttackPhase.SelectingAttacker]). All three share the same
 * interaction vocabulary:
 *
 * - arrow/wasd/qe → move the cursor (never seat-guarded — always legal),
 * - Enter / click → select the unit under the cursor (subject to the seat
 *   guard, ownership, and [selectGuard]) and enter the phase's sub-mode via
 *   [enterFor],
 * - Tab → cycle to the next unit in [selectableUnits] and *also* enter the
 *   sub-mode for it, so Tab and Enter land in the same place,
 * - 'c' → run [onCommit] (a no-op by default, as in movement).
 *
 * Click, Enter, Tab, and 'c' are all acting moves, so each is gated by
 * [localTurnGuard] first — it's not enough to guard the Enter/click path via
 * [selectOwnUnit], since Tab ([cycleAndEnter]) and 'c' ([onCommit]) never go
 * through it and would otherwise let a remote client act as the opponent.
 *
 * Returns null when the event maps to no idle action, matching [mapIdleInput].
 */
internal fun handleUnitSelection(
    event: InputEvent,
    app: AppState,
    activePlayer: () -> PlayerId,
    selectableUnits: () -> List<VisibleUnit>,
    selectGuard: (CombatUnit) -> FlashMessage? = { null },
    onCommit: (AppState) -> Transition = { Transition(it) },
    enterFor: (CombatUnit, AppState) -> Transition,
): Transition? {
    val action = mapIdleInput(event) ?: return null
    // [activePlayer] and [selectableUnits] are evaluated lazily: cursor moves must not touch
    // turn-state fields that may be absent (e.g. TurnState.NULL) when no unit selection is
    // actually happening. Every other branch is an acting move and calls [activePlayer]
    // unconditionally (via [localTurnGuard], and again directly for Click/Enter) — so this
    // function must only ever be entered once the caller's own turn-state field is already known
    // to be seeded. See [MovementPhase.SelectingUnit]'s `turnState.movement.isComplete` guard and
    // [AttackPhase.SelectingAttacker]'s / [PhysicalAttackPhase.SelectingAttacker]'s
    // `turnState.attack.isComplete` guard, each of which short-circuits to cursor-only handling
    // (never calling this function) while its sequence is unseeded.
    return when (action) {
        is IdleAction.MoveCursor -> handleCursorMove(app, action)
        is IdleAction.ClickHex ->
            localTurnGuard(app, activePlayer) ?: selectUnitAt(app.copy(cursor = action.coords), activePlayer(), selectGuard, enterFor)
        is IdleAction.SelectUnit ->
            localTurnGuard(app, activePlayer) ?: selectUnitAt(app, activePlayer(), selectGuard, enterFor)
        is IdleAction.CycleUnit -> localTurnGuard(app, activePlayer) ?: cycleAndEnter(app, selectableUnits(), enterFor)
        is IdleAction.CommitDeclarations -> localTurnGuard(app, activePlayer) ?: onCommit(app)
    }
}

/**
 * The [VisibleUnit] under the cursor in an idle selecting state.
 * [AppState.visibleState] has already decided
 * [battletech.tactical.unit.CombatUnit] vs [battletech.tactical.unit.ForeignUnit]
 * for [AppState.viewer], which is always a concrete seat (see [AppState.viewer]'s
 * KDoc). There is nothing left to redact here — the projection already did it.
 */
internal fun cursorUnitStatus(app: AppState): VisibleUnit? = app.visibleState.unitAt(app.cursor)

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
    selectableUnits: List<VisibleUnit>,
    enterFor: (CombatUnit, AppState) -> Transition,
): Transition {
    if (selectableUnits.isEmpty()) return Transition(app)
    val currentId = app.visibleState.unitAt(app.cursor)?.id
    val idx = selectableUnits.indexOfFirst { it.id == currentId }
    val next = selectableUnits[if (idx == -1) 0 else (idx + 1) % selectableUnits.size]
    return enterFor(app.ownUnit(next.id), app.copy(cursor = next.position))
}
