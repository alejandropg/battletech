package battletech.tactical.session

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.calculateMovementOrder
import battletech.tactical.action.rollInitiative
import battletech.tactical.command.GameCommand
import battletech.tactical.dice.DiceRoller
import battletech.tactical.event.InitiativeRolled
import battletech.tactical.model.GameState

/**
 * System phase. On entry, rolls initiative and seeds the movement sequence
 * for the new turn. Completes immediately so the cascade advances to
 * [MovementPhaseHandler]. Accepts no commands.
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
        turn.initiative.rolls.isNotEmpty() && turn.movementSequence.order.isNotEmpty()

    override fun onEntry(
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        val initiative = rollInitiative(roller)
        val loserCount = state.unitsOf(initiative.loser).size
        val winnerCount = state.unitsOf(initiative.winner).size
        val movementOrder = calculateMovementOrder(
            loser = initiative.loser, loserUnitCount = loserCount,
            winner = initiative.winner, winnerUnitCount = winnerCount,
        )
        val newTurn = TurnState(
            initiative,
            ImpulseSequence(movementOrder)
        )
        return PhaseOutcome(state, newTurn, listOf(InitiativeRolled(initiative)))
    }
}
