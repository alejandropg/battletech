package battletech.tactical.movement

import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.calculateMovementOrder
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.GameCommand
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.PhaseHandler
import battletech.tactical.session.PhaseOutcome
import battletech.tactical.session.TurnState
import battletech.tactical.session.UnitMoved

/**
 * Player phase. On entry, seeds the movement impulse sequence from initiative
 * results. Accepts [MoveUnit] for the unit owner whose impulse is current and
 * whose unit hasn't already moved this turn. The auto-advance cascade leaves
 * on [isComplete] (all movement impulses consumed).
 */
public class MovementPhaseHandler : PhaseHandler {

    override val phase: TurnPhase = TurnPhase.MOVEMENT

    override fun activePlayer(turn: TurnState): PlayerId? =
        if (turn.movementSequence.order.isEmpty() || turn.movementSequence.isComplete) null
        else turn.activePlayer

    override fun accepts(command: GameCommand, turn: TurnState): Boolean =
        command is MoveUnit

    override fun validate(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
    ): CommandRejection? {
        val cmd = command as MoveUnit
        val unit = state.unitById(cmd.unitId)
            ?: return CommandRejection.UnknownUnit(cmd.unitId)
        if (unit.owner != cmd.playerId) {
            return CommandRejection.NotYourTurn(activePlayer = unit.owner, attemptedBy = cmd.playerId)
        }
        if (cmd.unitId in turn.movedUnitIds) {
            return CommandRejection.UnitAlreadyActed(cmd.unitId)
        }
        return null
    }

    override fun apply(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        val cmd = command as MoveUnit
        val from = state.unitById(cmd.unitId)!!.position
        val newState = state.moveUnit(cmd.unitId, cmd.destination)
        val newTurn = turn.advanceAfterUnitMoved(cmd.unitId)
        val event = UnitMoved(
            unitId = cmd.unitId,
            from = from,
            to = cmd.destination.position,
            finalFacing = cmd.destination.facing,
            mode = cmd.mode,
            mpSpent = cmd.destination.mpSpent,
        )
        return PhaseOutcome(newState, newTurn, listOf(event))
    }

    override fun isComplete(turn: TurnState): Boolean =
        turn.movementSequence.order.isNotEmpty() && turn.movementSequence.isComplete

    override fun onEntry(
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        val initiative = turn.initiative
        val order = calculateMovementOrder(
            loser = initiative.loser, loserUnitCount = state.unitsOf(initiative.loser).size,
            winner = initiative.winner, winnerUnitCount = state.unitsOf(initiative.winner).size,
        )
        return PhaseOutcome(state, turn.copy(movementSequence = ImpulseSequence(order)), emptyList())
    }
}
