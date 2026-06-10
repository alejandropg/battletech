package battletech.tactical.movement

import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.heat.movementHeatSource

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
import battletech.tactical.session.StandUp
import battletech.tactical.session.TurnState
import battletech.tactical.session.UnitMoved
import battletech.tactical.session.UnitStoodUp
import battletech.tactical.unit.pilotingSkillRoll

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
        command is MoveUnit || command is StandUp

    override fun validate(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
    ): CommandRejection? = when (command) {
        is MoveUnit -> validateActivation(command.unitId, command.playerId, state, turn)
            ?: if (state.unitById(command.unitId)?.isProne == true) {
                CommandRejection.UnitProne(command.unitId)
            } else {
                null
            }

        is StandUp -> validateActivation(command.unitId, command.playerId, state, turn)
            ?: if (state.unitById(command.unitId)?.isProne != true) {
                CommandRejection.UnitNotProne(command.unitId)
            } else {
                null
            }

        else -> null
    }

    private fun validateActivation(
        unitId: battletech.tactical.unit.UnitId,
        playerId: PlayerId,
        state: GameState,
        turn: TurnState,
    ): CommandRejection? {
        val unit = state.unitById(unitId) ?: return CommandRejection.UnknownUnit(unitId)
        if (unit.owner != playerId) {
            return CommandRejection.NotYourTurn(activePlayer = unit.owner, attemptedBy = playerId)
        }
        if (unitId in turn.movedUnitIds) {
            return CommandRejection.UnitAlreadyActed(unitId)
        }
        return null
    }

    override fun apply(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome = when (command) {
        is StandUp -> applyStandUp(command, state, turn, roller)
        is MoveUnit -> applyMove(command, state, turn)
        else -> PhaseOutcome(state, turn, emptyList())
    }

    private fun applyStandUp(
        cmd: StandUp,
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        val unit = state.unitById(cmd.unitId)!!
        val psr = pilotingSkillRoll(unit, roller)
        return if (psr.passed) {
            // Stood up; activation NOT consumed so the unit may still move this impulse.
            val newState = state.copy(
                units = state.units.map { if (it.id == cmd.unitId) it.copy(isProne = false) else it },
            )
            PhaseOutcome(newState, turn, listOf(UnitStoodUp(cmd.unitId, psr, stoodUp = true)))
        } else {
            // Failed to rise; remains prone and its activation is spent.
            PhaseOutcome(state, turn.advanceAfterUnitMoved(cmd.unitId), listOf(UnitStoodUp(cmd.unitId, psr, stoodUp = false)))
        }
    }

    private fun applyMove(
        cmd: MoveUnit,
        state: GameState,
        turn: TurnState,
    ): PhaseOutcome {
        val from = state.unitById(cmd.unitId)!!.position
        val hexesMoved = hexesMoved(from, cmd.destination)
        val heatSource = movementHeatSource(cmd.mode, hexesMoved)
        val movedState = state.moveUnit(cmd.unitId, cmd.destination)
        val newState = movedState.copy(
            units = movedState.units.map { unit ->
                if (unit.id == cmd.unitId) {
                    unit.copy(
                        movementThisTurn = MovementThisTurn(cmd.mode, hexesMoved),
                        heatGeneratedThisTurn = unit.heatGeneratedThisTurn +
                            listOfNotNull(heatSource),
                    )
                } else {
                    unit
                }
            },
        )
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
        // Shutdown 'Mechs can't move, so they don't take an impulse slot.
        val order = calculateMovementOrder(
            loser = initiative.loser, loserUnitCount = state.activeUnitsOf(initiative.loser).size,
            winner = initiative.winner, winnerUnitCount = state.activeUnitsOf(initiative.winner).size,
        )
        // Clear last turn's movement so attacker/target movement modifiers
        // reflect only this turn's movement.
        val resetState = state.copy(
            units = state.units.map { it.copy(movementThisTurn = MovementThisTurn.STATIONARY) },
        )
        return PhaseOutcome(resetState, turn.copy(movementSequence = ImpulseSequence(order)), emptyList())
    }
}

/** Hexes actually entered along [destination]'s path (turn-in-place steps excluded). */
public fun hexesMoved(from: battletech.tactical.model.HexCoordinates, destination: ReachableHex): Int {
    val positions = listOf(from) + destination.path.map { it.position }
    return positions.zipWithNext().count { (previous, next) -> previous != next }
}
