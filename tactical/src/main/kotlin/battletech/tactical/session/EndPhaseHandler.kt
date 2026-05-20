package battletech.tactical.session

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.action.attack.resetTorsoFacings

/**
 * System phase. On entry, resets torso facings (units' torsos snap back to
 * leg facing) and emits a turn-end event. The cascade then rolls over to
 * [InitiativePhaseHandler], which rebuilds a fresh [TurnState] for the
 * next turn.
 */
public class EndPhaseHandler : PhaseHandler {

    override val phase: TurnPhase = TurnPhase.END

    override fun activePlayer(turn: TurnState): PlayerId? = null

    override fun accepts(command: GameCommand, turn: TurnState): Boolean = false

    override fun apply(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome = PhaseOutcome(state, turn, emptyList())

    override fun isComplete(turn: TurnState): Boolean = true

    override fun onEntry(
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        val resetState = state.resetTorsoFacings()
        return PhaseOutcome(resetState, turn, listOf(TurnEnded(turnNumber = 0)))
    }
}
