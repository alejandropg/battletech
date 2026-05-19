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
import battletech.tui.game.phase.Phase

/**
 * The TUI's UI-shell state. Holds a reference to the authoritative
 * [BattleSession] (the source of truth for gameState and turnState) plus
 * pure UI state: which [Phase] sub-state machine is active and where the
 * cursor sits on the board.
 *
 * The [gameState] and [turnState] accessors read through the session;
 * mutations flow via `session.submitCommand(...)` or, transitionally,
 * `session.applyMutation { ... }`. PR7 removes the back-door once all
 * phase-progression lives in PR6 PhaseHandlers.
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
 * Back-compat factory matching the pre-PR5 positional signature so the
 * existing call sites (production and tests) keep compiling. Constructs a
 * fresh [BattleSession] around the supplied state. Once everything routes
 * through an externally-owned session (PR7+), this factory becomes
 * unnecessary and can be removed.
 */
public fun AppState(
    gameState: GameState,
    turnState: TurnState = TurnState.NULL,
    phase: Phase,
    cursor: HexCoordinates,
    roller: DiceRoller = RandomDiceRoller(),
): AppState = AppState(
    session = BattleSession(gameState, turnState, roller),
    phase = phase,
    cursor = cursor,
)

public fun moveCursor(
    cursor: HexCoordinates,
    direction: HexDirection,
    map: GameMap,
): HexCoordinates {
    val neighbor = cursor.neighbor(direction)
    return if (neighbor in map.hexes) neighbor else cursor
}
