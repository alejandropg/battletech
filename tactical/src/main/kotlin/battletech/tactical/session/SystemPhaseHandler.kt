package battletech.tactical.session

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId

/**
 * Base for phases the game itself drives rather than a player — Initiative, Heat, End.
 * These do all their work in [onEntry] (roll initiative, dissipate heat, reset for next
 * turn), accept no commands ([accepts] always false, [activePlayer] always null,
 * [apply] a no-op), and complete immediately so the cascade proceeds to the next phase.
 *
 * [isComplete] defaults to `true` — the common case, since these phases finish the
 * instant [onEntry] runs — but is `open`: [InitiativePhaseHandler] overrides it to gate
 * on `turn.initiative.rolls` actually having landed.
 */
public abstract class SystemPhaseHandler : PhaseHandler {

    final override fun activePlayer(turn: TurnState): PlayerId? = null

    final override fun accepts(command: GameCommand, turn: TurnState): Boolean = false

    final override fun apply(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome = PhaseOutcome(state, turn, emptyList())

    override fun isComplete(turn: TurnState): Boolean = true
}
