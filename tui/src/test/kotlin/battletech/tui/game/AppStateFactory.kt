package battletech.tui.game

import battletech.tactical.dice.DiceRoller
import battletech.tactical.dice.RandomDiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.BattleSession
import battletech.tactical.session.GameSession
import battletech.tactical.session.TurnState
import battletech.tui.game.phase.Phase

/**
 * Builds an [AppState] pinned at [phase] with a pre-seeded [turnState], for tests of UI phase
 * logic ([Phase.handle] and friends) — which are pure functions over [AppState], and for which
 * the session is a stub.
 *
 * Hands ONE [BattleSession] to both seats. That is a legal composition — [BattleSession.stateFor]
 * projects for any viewer — but it is deliberately NOT how production composes hot-seat, which is
 * a `GameServer` plus two `connectLocal()` clients (see `Main.kt`). The difference is intentional
 * and this fixture is the reason it stays cheap:
 *  - a real per-seat client is bound to the seat the server assigned it, so it cannot be relabeled
 *    to stand in for the opponent — a trick [SeatGuardTest] relies on to build a single-seat view.
 *  - [BattleSession.annotate] has no [GameSession] equivalent, and [battletech.tui.TuiAppLoopTest]
 *    needs it to inject a notice.
 *  - each `connectLocal()` seat costs three daemon threads, and this factory hands back only an
 *    [AppState] — no caller could close them. Measured at ~750 unclosable threads across the tui
 *    suite, to exercise a transport these tests do not test.
 *
 * `HotSeatCompositionTest` covers the real composition at the altitude where it matters, and the
 * `network` module's own suites (`ClientGameSessionTest`, `LocalhostEndToEndTest`) own the
 * client/server behavior itself.
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
