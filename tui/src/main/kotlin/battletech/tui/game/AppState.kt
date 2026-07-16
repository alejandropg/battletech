package battletech.tui.game

import battletech.tactical.attack.AttackResult
import battletech.tactical.dice.DiceRoller
import battletech.tactical.dice.RandomDiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.PlayerGameState
import battletech.tactical.query.PlayerView
import battletech.tactical.session.BattleSession
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

/**
 * The TUI's UI-shell state. [seats] is the set of seats this process drives, each mapped to the
 * [GameSession] that seat submits commands and reads redacted state through — hot-seat maps both
 * [PlayerId]s to the same shared session, `--host`/`--join` map exactly the one local seat. There
 * is no separate "is this hot-seat?" flag anywhere in this class: a seat's mere presence or
 * absence in [seats] IS the answer, for both viewer pinning ([viewer]) and input gating (the seat
 * check in `battletech.tui.game.phase`'s `localTurnGuard`) alike.
 *
 * Every seat's session is a replica of the same authoritative match, so [turnState] and the
 * domain-level fields on [GameSession] ([GameSession.currentPhase], [GameSession.activePlayer])
 * agree no matter which seat's session answers them — see [anySession]. Only per-viewer reads
 * ([visibleState], [viewFor], [stateFor], [logFor]) and command submission ([submitCommand]) are
 * seat-specific, because a remote seat's [GameSession] only ever knows how to act and project for
 * itself; those go through that exact seat's entry in [seats].
 *
 * [visibleState] is the ONLY view of game state a delivery may read — see its KDoc.
 */
internal data class AppState(
    val seats: Map<PlayerId, GameSession>,
    val phase: Phase,
    val cursor: HexCoordinates,
    val collapsedPanels: Set<Int> = emptySet(),
    val lastAttackResults: List<AttackResult>? = null,
    val panelScrollOffsets: Map<Int, Int> = emptyMap(),
    val matchEnded: MatchEnded? = null,
) {
    /**
     * Any seat's session — safe ONLY for fields every replica agrees on ([turnState],
     * [GameSession.currentPhase], [GameSession.activePlayer]). Never use this for a per-viewer
     * read ([visibleState], [viewFor], [stateFor], [logFor]) or for [submitCommand] — those must
     * go through the specific seat in question via [seats].
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

    /** The only state the TUI can see. */
    val visibleState: PlayerGameState get() = seats.getValue(viewer).stateFor(viewer)

    fun viewFor(player: PlayerId): PlayerView = seats.getValue(player).viewFor(player)

    /** [PlayerGameState] projected for [player] specifically — [visibleState] is the viewer-scoped shorthand most callers want. */
    fun stateFor(player: PlayerId): PlayerGameState = seats.getValue(player).stateFor(player)

    /** [LogEntry] history redacted for [player], via that seat's own session. */
    fun logFor(player: PlayerId): List<LogEntry> = seats.getValue(player).logFor(player)

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
     * ([selectOwnUnit][battletech.tui.game.phase.selectOwnUnit]). Throws if the projection
     * disagrees: that would mean the call site's ownership assumption was wrong, which should
     * fail loudly rather than silently render nothing (or, worse, leak).
     *
     * Delegates to [PlayerGameState.ownUnitById] — the rules engine's query path resolves its
     * actor through that same single implementation, so the TUI and the engine cannot drift
     * on what "the viewer's own unit" means.
     */
    fun ownUnit(id: UnitId): CombatUnit = visibleState.ownUnitById(id)
}

/**
 * Back-compat factory matching the pre-PR5 positional signature. Constructs
 * a [BattleSession] positioned at the supplied [phase]'s domain phase so
 * the session and TUI agree on where in the turn we are, and hands it to
 * BOTH seats — a legal hot-seat composition, since [BattleSession.stateFor]
 * projects for any viewer. Used primarily by tests that pre-seed a known
 * [turnState].
 */
internal fun AppState(
    gameState: GameState,
    turnState: TurnState = TurnState.NULL,
    phase: Phase,
    cursor: HexCoordinates,
    roller: DiceRoller = RandomDiceRoller(),
): AppState {
    val session: GameSession = BattleSession(
        initialGameState = gameState,
        initialTurnState = turnState,
        roller = roller,
        initialPhase = phase.turnPhase,
        initialNeedsOnEntry = inferNeedsOnEntry(phase.turnPhase, turnState),
    )
    return AppState(
        seats = PlayerId.entries.associateWith { session },
        phase = phase,
        cursor = cursor,
    )
}

/**
 * Decide whether the session should fire on-entry for the starting phase.
 *
 * Player phases drive on-entry from state markers (e.g., attackSequence
 * empty ⇒ WeaponAttack hasn't seeded yet ⇒ on-entry pending). System
 * phases have no neutral marker; assume on-entry is pending if the caller
 * hasn't pre-supplied a turnState.
 */
private fun inferNeedsOnEntry(phase: TurnPhase, turn: TurnState): Boolean = when (phase) {
    TurnPhase.INITIATIVE -> turn.initiative.rolls.isEmpty()
    TurnPhase.MOVEMENT -> false
    TurnPhase.WEAPON_ATTACK -> turn.attack.sequence.order.isEmpty()
    TurnPhase.PHYSICAL_ATTACK -> turn.attack.sequence.order.isEmpty()
    TurnPhase.HEAT -> turn === TurnState.NULL
    TurnPhase.END -> turn === TurnState.NULL
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
