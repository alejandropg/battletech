package battletech.tui.game

import battletech.tactical.action.TurnPhase
import battletech.tactical.dice.DiceRoller
import battletech.tactical.dice.RandomDiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.session.BattleSession
import battletech.tactical.session.TurnState
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.EndPhase
import battletech.tui.game.phase.HeatPhase
import battletech.tui.game.phase.InitiativePhase
import battletech.tui.game.phase.MovementPhase
import battletech.tui.game.phase.Phase

/**
 * The TUI's UI-shell state. Holds a reference to the authoritative
 * [BattleSession] (the source of truth for gameState and turnState) plus
 * pure UI state: which [Phase] sub-state machine is active and where the
 * cursor sits on the board.
 *
 * The [gameState] and [turnState] accessors read through the session;
 * mutations flow via `session.submitCommand(...)` or `session.advance()`.
 */
public data class AppState(
    public val session: BattleSession,
    public val phase: Phase,
    public val cursor: HexCoordinates,
) {
    public val gameState: GameState get() = session.gameState
    public val turnState: TurnState get() = session.turnState
    public val currentPhase: TurnPhase get() = phase.turnPhase
}

/**
 * Back-compat factory matching the pre-PR5 positional signature. Constructs
 * a [BattleSession] positioned at the supplied [phase]'s domain phase so
 * the session and TUI agree on where in the turn we are.
 *
 * - If [turnState] is [TurnState.NULL] (the fresh-game default), the
 *   session is set up so its first [BattleSession.advance] call fires
 *   the starting phase's on-entry work (e.g. roll initiative).
 * - Otherwise the test/caller has pre-seeded a turnState that already
 *   reflects the chosen phase; on-entry is treated as already done.
 */
public fun AppState(
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
 * phases (Heat / End) have no neutral marker; assume on-entry is pending
 * if the caller hasn't pre-supplied a turnState.
 */
private fun inferNeedsOnEntry(phase: TurnPhase, turn: TurnState): Boolean = when (phase) {
    TurnPhase.INITIATIVE -> turn.initiative.rolls.isEmpty()
    TurnPhase.MOVEMENT -> false
    TurnPhase.WEAPON_ATTACK -> turn.attackSequence.order.isEmpty()
    TurnPhase.PHYSICAL_ATTACK -> turn.attackSequence.order.isEmpty()
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
 * Map the current domain [TurnPhase] to the TUI [Phase] sub-state machine
 * we should be in. Used by the tick/transition path to re-sync app.phase
 * with session.currentPhase after the session's auto-advance cascade.
 */
public fun mapToTuiPhase(domainPhase: TurnPhase): Phase = when (domainPhase) {
    TurnPhase.INITIATIVE -> InitiativePhase
    TurnPhase.MOVEMENT -> MovementPhase.SelectingUnit
    TurnPhase.WEAPON_ATTACK -> AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)
    TurnPhase.PHYSICAL_ATTACK -> AttackPhase.SelectingAttacker(TurnPhase.PHYSICAL_ATTACK)
    TurnPhase.HEAT -> HeatPhase
    TurnPhase.END -> EndPhase
}
