package battletech.tactical.session

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.TurnPhase

/**
 * System phase. On entry, rolls initiative for the new turn. Completes
 * immediately so the cascade advances to [MovementPhaseHandler]. Accepts no commands.
 */
public class InitiativePhaseHandler : SystemPhaseHandler() {

    override val phase: TurnPhase = TurnPhase.INITIATIVE

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
