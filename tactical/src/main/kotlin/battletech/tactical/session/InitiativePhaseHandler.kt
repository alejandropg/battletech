package battletech.tactical.session

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase

/**
 * System phase. On entry, rolls initiative for the new turn. Completes
 * immediately so the cascade advances to [MovementPhaseHandler]. Accepts no commands.
 */
public class InitiativePhaseHandler : PhaseHandler {

    override val phase: TurnPhase = TurnPhase.INITIATIVE

    override fun activePlayer(turn: TurnState): PlayerId? = null

    override fun accepts(command: GameCommand, turn: TurnState): Boolean = false

    override fun apply(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome = PhaseOutcome(state, turn, emptyList())

    override fun isComplete(turn: TurnState): Boolean =
        turn.initiative.rolls.isNotEmpty()

    override fun onEntry(
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        val initiative = rollInitiative(roller)
        return PhaseOutcome(
            state = state,
            turn = TurnState(initiative = initiative, turnNumber = turn.turnNumber),
            events = listOf(InitiativeRolled(initiative)),
        )
    }
}
