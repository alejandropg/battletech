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
import battletech.tactical.query.PlayerView
import battletech.tactical.session.BattleSession
import battletech.tactical.session.TurnState
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.MovementPhase
import battletech.tui.game.phase.Phase
import battletech.tui.game.phase.PhysicalAttackPhase

/**
 * The TUI's UI-shell state. Holds a reference to the authoritative
 * [BattleSession] (the source of truth for gameState and turnState) plus
 * pure UI state: which [Phase] sub-state machine is active and where the
 * cursor sits.
 *
 * The [gameState] and [turnState] accessors read through the session;
 * mutations flow via `session.submitCommand(...)`.
 */
public data class AppState(
    public val session: BattleSession,
    public val phase: Phase,
    public val cursor: HexCoordinates,
    public val collapsedPanels: Set<Int> = emptySet(),
    public val lastAttackResults: List<AttackResult>? = null,
) {
    public val gameState: GameState get() = session.gameState
    public val turnState: TurnState get() = session.turnState
    public val currentPhase: TurnPhase get() = phase.turnPhase

    public fun viewFor(player: PlayerId): PlayerView = session.viewFor(player)
}

/**
 * Back-compat factory matching the pre-PR5 positional signature. Constructs
 * a [BattleSession] positioned at the supplied [phase]'s domain phase so
 * the session and TUI agree on where in the turn we are. Used primarily by
 * tests that pre-seed a known [turnState].
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
 * phases have no neutral marker; assume on-entry is pending if the caller
 * hasn't pre-supplied a turnState.
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
 * Map the current domain [TurnPhase] to the TUI [Phase] sub-state machine.
 * Only player phases (Movement, Weapon/Physical Attack) have TUI phase
 * objects; if the session is somehow observed in a system phase, the TUI
 * presents a generic SelectingUnit/SelectingAttacker placeholder — the
 * cascade should drive past system phases before the next render.
 */
public fun mapToTuiPhase(domainPhase: TurnPhase): Phase = when (domainPhase) {
    TurnPhase.MOVEMENT,
    TurnPhase.INITIATIVE,
    TurnPhase.HEAT,
    TurnPhase.END,
    -> MovementPhase.SelectingUnit
    TurnPhase.WEAPON_ATTACK -> AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)
    TurnPhase.PHYSICAL_ATTACK -> PhysicalAttackPhase.SelectingAttacker()
}
