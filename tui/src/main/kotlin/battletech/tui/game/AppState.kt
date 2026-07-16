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
import battletech.tactical.session.GameSession
import battletech.tactical.session.MatchEnded
import battletech.tactical.session.TurnState
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.MovementPhase
import battletech.tui.game.phase.Phase
import battletech.tui.game.phase.PhysicalAttackPhase

/**
 * The TUI's UI-shell state. Holds a reference to the [GameSession] (the
 * source of truth for turnState) plus pure UI state: which [Phase]
 * sub-state machine is active and where the cursor sits.
 *
 * [visibleState] is the ONLY view of game state a delivery may read — see its
 * KDoc. [turnState] mutations flow via `session.submitCommand(...)`.
 */
internal data class AppState(
    val session: GameSession,
    val phase: Phase,
    val cursor: HexCoordinates,
    val collapsedPanels: Set<Int> = emptySet(),
    val lastAttackResults: List<AttackResult>? = null,
    val panelScrollOffsets: Map<Int, Int> = emptyMap(),
    val matchEnded: MatchEnded? = null,
    val localPlayer: PlayerId? = null,
) {
    val turnState: TurnState get() = session.turnState
    val currentPhase: TurnPhase get() = phase.turnPhase

    /**
     * Who the screen is drawn for. A remote client is pinned to its own seat via [localPlayer];
     * hot-seat follows the acting player. Falls back to PLAYER_1 only for the transient
     * system-phase windows (Initiative/Heat/End) where [GameSession.activePlayer] is momentarily
     * null — never a resting render state, since the cascade (see [mapToTuiPhase]'s KDoc) drives
     * past system phases before the next render.
     */
    val viewer: PlayerId get() = localPlayer ?: session.activePlayer ?: PlayerId.PLAYER_1

    /** The only state the TUI can see. */
    val visibleState: PlayerGameState get() = session.stateFor(viewer)

    fun viewFor(player: PlayerId): PlayerView = session.viewFor(player)

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
 * the session and TUI agree on where in the turn we are. Used primarily by
 * tests that pre-seed a known [turnState].
 */
internal fun AppState(
    gameState: GameState,
    turnState: TurnState = TurnState.NULL,
    phase: Phase,
    cursor: HexCoordinates,
    roller: DiceRoller = RandomDiceRoller(),
): AppState = AppState(
    session = BattleSession(
        initialGameState = gameState,
        initialTurnState = turnState,
        roller = roller,
        initialPhase = phase.turnPhase,
        initialNeedsOnEntry = inferNeedsOnEntry(phase.turnPhase, turnState),
    ),
    phase = phase,
    cursor = cursor,
)

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
