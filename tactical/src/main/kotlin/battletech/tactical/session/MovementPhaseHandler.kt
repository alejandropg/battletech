package battletech.tactical.session

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.command.CommandRejection
import battletech.tactical.command.GameCommand
import battletech.tactical.command.MoveUnit
import battletech.tactical.dice.DiceRoller
import battletech.tactical.event.UnitMoved
import battletech.tactical.model.GameState

/**
 * Player phase. Accepts [MoveUnit] for the unit owner whose impulse is
 * current and whose unit hasn't already moved this turn. The auto-advance
 * cascade leaves on [isComplete] (all movement impulses consumed).
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
}
