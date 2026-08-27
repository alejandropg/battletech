package battletech.tui.game

import battletech.tactical.attack.AttackResult
import battletech.tactical.model.GameMap
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.PlayerGameState
import battletech.tactical.query.PlayerView
import battletech.tactical.session.CommandResult
import battletech.tactical.session.GameCommand
import battletech.tactical.session.GameSession
import battletech.tactical.session.LogEntry
import battletech.tactical.session.MatchEnded
import battletech.tactical.session.TurnState
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.MovementPhase
import battletech.tui.game.phase.Phase
import battletech.tui.game.phase.PhysicalAttackPhase
import tenter.view.ScrollOffset

/**
 * The TUI's UI-shell state. [seats] is the set of seats this process drives, each mapped to the
 * [GameSession] that seat submits commands and reads redacted state through. A seat's presence
 * or absence in [seats] is what both viewer pinning ([viewer]) and input gating (the seat check
 * in `battletech.tui.game.phase`'s `localTurnGuard`) key off.
 *
 * Every seat's session is a replica of the same authoritative match, so [turnState] and the
 * domain-level fields on [GameSession] ([GameSession.currentPhase], [GameSession.activePlayer])
 * agree no matter which seat's session answers them — see [anySession]. Only the per-viewer reads
 * ([state], [view], [log]) and command submission ([submitCommand]) are seat-specific, because a
 * remote seat's [GameSession] only ever knows how to act and project for itself; those go through
 * that exact seat's entry in [seats].
 *
 * [state], [view] and [log] are the ONLY game state a delivery may read — see their KDoc.
 */
internal data class AppState(
    val seats: Map<PlayerId, GameSession>,
    val phase: Phase,
    val cursor: HexCoordinates,
    // Whether the HELP panel exists this frame — a user action recorded here (like any other
    // AppState field a phase or event can drive), NOT a display preference. It feeds
    // battletech.tui.game.PanelVisibility, which tenter.panel.Panel cannot see; a panel's own
    // state (minimized/normal/maximized) and scroll, in contrast, have no reader outside their
    // own rendering and so live on the Panel instead. See PanelVisibility's KDoc for the `Alt+h`
    // vs `Alt+<key>` distinction this split exists to express.
    val helpOpen: Boolean = false,
    val lastAttackResults: List<AttackResult>? = null,
    val matchEnded: MatchEnded? = null,
    // A read-only mirror of the board panel's settled offset, written after every render solely
    // so the phases' click-to-hex mapping (SelectingCommon) can stay a pure `(event, state) ->
    // Transition` function without reaching into Workspace. Nothing writes it except RunLoop's
    // post-render sync — unlike a side panel's scroll, which only rendering ever reads and so
    // lives on the Panel itself (see tenter.panel.Panel's KDoc), this is the one piece of panel
    // state both readers (rendering and input mapping) need to see.
    val boardScroll: ScrollOffset = ScrollOffset.ZERO,
) {
    /**
     * Any seat's session — safe ONLY for fields every replica agrees on ([turnState],
     * [GameSession.currentPhase], [GameSession.activePlayer]). Never use this for a per-viewer
     * read ([state], [view], [log]) or for [submitCommand] — those must go through the
     * specific seat in question via [seats].
     */
    internal val anySession: GameSession get() = seats.values.first()

    val turnState: TurnState get() = anySession.turnState
    val currentPhase: TurnPhase get() = phase.turnPhase

    /**
     * Who the screen is drawn for. Follows [GameSession.activePlayer] whenever that player is a
     * seat this process drives — which is unconditionally true in hot-seat, since [seats] holds
     * both players there, so the view follows the acting player exactly as before. In host/join
     * play (one seat in [seats]) it's true only on this client's own turn; otherwise (watching the
     * opponent act, or the transient system-phase windows where [GameSession.activePlayer] is
     * momentarily null) it falls back to the lowest seat this process drives — [seats]' only entry
     * for host/join, [PlayerId.PLAYER_1] for hot-seat — mirroring today's `?: PLAYER_1` fallback.
     * Never a resting render state in hot-seat, since the cascade (see [mapToTuiPhase]'s KDoc)
     * drives past system phases before the next render.
     */
    val viewer: PlayerId get() = anySession.activePlayer?.takeIf { it in seats } ?: seats.keys.min()

    /**
     * The read path, and the only game state the TUI can see. All three are projected for
     * [viewer] — the seat this process actually drives — and take no player argument on purpose:
     * there is no way to ask this type for another seat's projection.
     *
     * That used to be a caller-side rule rather than a property of this interface, and a caller
     * got it wrong: the DECLARED TARGETS panel scoped itself to the globally active attacker,
     * which in host/join play is routinely the opponent, and every render during the opponent's
     * attack impulse died in `Map.getValue`. Because [viewer] is a member of [seats] by
     * construction, these lookups cannot miss and that bug is no longer expressible.
     *
     * The seat whose projection this is therefore follows [viewer], not the caller: in hot-seat
     * it tracks the active player; in host/join it is this process's one seat. Redaction within
     * a projection stays type-enforced one level down, in [PlayerGameState].
     *
     * [submitCommand] is deliberately NOT part of this — a command names the seat it acts for,
     * and in hot-seat that is legitimately either of them.
     */
    val state: PlayerGameState get() = seats.getValue(viewer).stateFor(viewer)

    /** [viewer]'s query surface: legal movement, target infos, declared attacks. See [state]. */
    val view: PlayerView get() = seats.getValue(viewer).viewFor(viewer)

    /** [LogEntry] history redacted for [viewer]. See [state]. */
    val log: List<LogEntry> get() = seats.getValue(viewer).logFor(viewer)

    /**
     * Submits [command] through the session for the seat it names ([GameCommand.playerId]).
     * Every acting command reaches this point only once a seat check has already confirmed that
     * seat is one this process drives (see the seat-check guard in `battletech.tui.game.phase`),
     * so the lookup below never misses.
     */
    fun submitCommand(command: GameCommand): CommandResult = seats.getValue(command.playerId).submitCommand(command)

    /**
     * The full [CombatUnit] for [id], for call sites that already know [id] names a unit the
     * viewer owns — e.g. the attacker/mover reached via an ownership-gated selection
     * ([selectOwnUnit][battletech.tui.game.phase.selectOwnUnit]). Delegates to
     * [PlayerGameState.ownUnitById], which owns the throw-on-mismatch rule.
     */
    fun ownUnit(id: UnitId): CombatUnit = state.ownUnitById(id)
}

public fun moveCursor(
    cursor: HexCoordinates,
    direction: HexDirection,
    map: GameMap,
): HexCoordinates {
    val neighbor = cursor.neighbor(direction)
    return if (neighbor in map.hexes) neighbor else cursor
}

/**
 * Map the current domain [TurnPhase] to the TUI [Phase] sub-state machine.
 * Only player phases (Movement, Weapon/Physical Attack) have TUI phase
 * objects; if the session is somehow observed in a system phase, the TUI
 * presents a generic SelectingUnit/SelectingAttacker placeholder — the
 * cascade should drive past system phases before the next render.
 */
internal fun mapToTuiPhase(domainPhase: TurnPhase): Phase = when (domainPhase) {
    TurnPhase.MOVEMENT,
    TurnPhase.INITIATIVE,
    TurnPhase.HEAT,
    TurnPhase.END,
    -> MovementPhase.SelectingUnit
    TurnPhase.WEAPON_ATTACK -> AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)
    TurnPhase.PHYSICAL_ATTACK -> PhysicalAttackPhase.SelectingAttacker()
}
