package battletech.tui.game.phase

import battletech.tactical.model.PlayerId
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommandResult
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.VisibleUnit
import battletech.tui.game.AppState
import io.archinaut.tenter.view.FlashMessage
import battletech.tui.game.moveCursor
import battletech.tui.input.BoardClick
import battletech.tui.input.IdleAction
import io.archinaut.tenter.input.InputAction

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
 * Handle a [IdleAction.MoveCursor] action: move the cursor one step in the
 * given direction (clamped to map boundaries) and return the resulting
 * [Transition].
 */
internal fun handleCursorMove(app: AppState, action: IdleAction.MoveCursor): Transition =
    Transition(app.copy(cursor = moveCursor(app.cursor, action.direction, app.state.map)))

/**
 * Guard against acting outside a seat this process drives.
 *
 * Returns `Transition(app, FlashMessage("Waiting for opponent"))` when [activePlayer] is not one
 * of [AppState.seats], else `null` so the caller can fall through to the real handling via `?:`.
 * Hot-seat never flashes — not because anything here special-cases hot-seat, but because
 * [AppState.seats] holds both players there, so [activePlayer] is always a member.
 *
 * [activePlayer] is evaluated only when an acting action reaches this guard. Cursor movement can
 * therefore remain safe while a phase has not seeded its turn-state field.
 */
private fun localTurnGuard(app: AppState, activePlayer: () -> PlayerId): Transition? =
    if (activePlayer() in app.seats) null else Transition(app, FlashMessage("Waiting for opponent"))

/**
 * Try to select the unit at the cursor as the active player's unit.
 *
 * - No unit at cursor → `Transition(app)` (no-op).
 * - [activePlayer] is not a seat this process drives → a waiting flash.
 * - Unit owned by someone other than [activePlayer] → a not-your-unit flash.
 * - [extraGuard] returns a non-null [FlashMessage] → that message.
 * - Otherwise → [onSelect].
 *
 * The [extraGuard] is evaluated only after the ownership check passes, so ownership always takes
 * priority over phase-specific guards.
 */
internal fun selectOwnUnit(
    app: AppState,
    activePlayer: PlayerId,
    extraGuard: (CombatUnit) -> FlashMessage? = { null },
    onSelect: (CombatUnit) -> Transition,
): Transition {
    val visible = app.state.units.at(app.cursor) ?: return Transition(app)
    localTurnGuard(app) { activePlayer }?.let { return it }
    if (visible.owner != activePlayer) return Transition(app, FlashMessage("Not your unit"))
    val unit = app.ownUnit(visible.id)
    extraGuard(unit)?.let { return Transition(app, it) }
    return onSelect(unit)
}

/**
 * Shared input handler for every "select a unit" idle state. All three selection phases share
 * the same interaction vocabulary: cursor moves are always legal; Enter/click, Tab, and commit
 * are acting moves and pass through [localTurnGuard].
 */
internal fun handleUnitSelection(
    action: InputAction,
    app: AppState,
    activePlayer: () -> PlayerId,
    selectableUnits: () -> List<VisibleUnit>,
    selectGuard: (CombatUnit) -> FlashMessage? = { null },
    onCommit: (AppState) -> Transition = { Transition(it) },
    enterFor: (CombatUnit, AppState) -> Transition,
): Transition? = when (action) {
    // [activePlayer] and [selectableUnits] are evaluated lazily: cursor moves must not touch
    // turn-state fields that may be absent when no unit selection is actually happening.
    is BoardClick ->
        localTurnGuard(app, activePlayer) ?: selectUnitAt(app.copy(cursor = action.coords), activePlayer(), selectGuard, enterFor)
    is IdleAction -> when (action) {
        is IdleAction.MoveCursor -> handleCursorMove(app, action)
        is IdleAction.SelectUnit ->
            localTurnGuard(app, activePlayer) ?: selectUnitAt(app, activePlayer(), selectGuard, enterFor)
        is IdleAction.CycleUnit -> localTurnGuard(app, activePlayer) ?: cycleAndEnter(app, selectableUnits(), enterFor)
        is IdleAction.CommitDeclarations -> localTurnGuard(app, activePlayer) ?: onCommit(app)
    }
    else -> null
}

/**
 * The [VisibleUnit] under the current cursor. [AppState.state] has already applied the viewer's
 * projection, so this helper does not repeat redaction checks.
 */
internal fun cursorUnitStatus(app: AppState): VisibleUnit? = app.state.units.at(app.cursor)

private fun selectUnitAt(
    app: AppState,
    activePlayer: PlayerId,
    selectGuard: (CombatUnit) -> FlashMessage?,
    enterFor: (CombatUnit, AppState) -> Transition,
): Transition = selectOwnUnit(app, activePlayer, selectGuard) { unit -> enterFor(unit, app) }

/**
 * Advance the cursor to the next unit in [selectableUnits] and enter its sub-mode via [enterFor].
 * An empty list is a no-op; a cursor outside the list enters the first unit and otherwise cycles
 * forward.
 */
private fun cycleAndEnter(
    app: AppState,
    selectableUnits: List<VisibleUnit>,
    enterFor: (CombatUnit, AppState) -> Transition,
): Transition {
    if (selectableUnits.isEmpty()) return Transition(app)
    val currentId = app.state.units.at(app.cursor)?.id
    val idx = selectableUnits.indexOfFirst { it.id == currentId }
    val next = selectableUnits[if (idx == -1) 0 else (idx + 1) % selectableUnits.size]
    return enterFor(app.ownUnit(next.id), app.copy(cursor = next.position))
}
