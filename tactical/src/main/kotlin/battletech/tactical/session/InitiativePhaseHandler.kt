package battletech.tactical.session

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.rollInitiative
import battletech.tactical.command.GameCommand
import battletech.tactical.dice.DiceRoller
import battletech.tactical.event.InitiativeRolled
import battletech.tactical.model.GameState

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
        return PhaseOutcome(state, TurnState(initiative), listOf(InitiativeRolled(initiative)))
    }
}
