package battletech.tactical.movement

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
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
import battletech.tactical.session.calculateMovementOrder
import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.basePsrModifier
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
        if (turn.movement.sequence.order.isEmpty() || turn.movement.isComplete) null
        else turn.movement.activePlayer

    override fun accepts(command: GameCommand, turn: TurnState): Boolean =
        command is MoveUnit || command is StandUp

    override fun validate(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
    ): CommandRejection? = when (command) {
        is MoveUnit -> validateActivation(command.unitId, command.playerId, state, turn)
            ?: MovementRules.moveRejection(state.unitById(command.unitId), command.mode)
            ?: validateDestination(command, state)

        is StandUp -> validateActivation(command.unitId, command.playerId, state, turn)
            ?: MovementRules.standUpRejection(state.unitById(command.unitId))

        else -> null
    }

    /**
     * Server-authoritative destination check. Rejects a [MoveUnit] whose
     * [MoveUnit.destination] is outside the unit's legal reach, or whose
     * path/mpSpent were tampered to differ from the server-computed value.
     * This is the ONLY place the reachability Dijkstra runs for a given move —
     * [applyMove] trusts the already-validated command instead of re-deriving it
     * (see that method's doc for why the check has to live here, not there).
     */
    private fun validateDestination(
        cmd: MoveUnit,
        state: GameState,
    ): CommandRejection? {
        val unit = state.unitById(cmd.unitId) // existence guaranteed by validateActivation
        return when (val destination = MovementRules.authoritativeDestination(unit, cmd.mode, cmd.destination, state)) {
            is AuthoritativeDestination.Legal -> null
            is AuthoritativeDestination.Illegal -> destination.rejection
        }
    }

    private fun validateActivation(
        unitId: UnitId,
        playerId: PlayerId,
        state: GameState,
        turn: TurnState,
    ): CommandRejection? {
        val unit = state.unitById(unitId)
        if (unit.owner != playerId) {
            return CommandRejection.NotYourUnit(unitId, owner = unit.owner, attemptedBy = playerId)
        }
        if (unitId in turn.movement.movedUnitIds) {
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
        val unit = state.unitById(cmd.unitId)
        val modifier = unit.basePsrModifier()
        val psr = pilotingSkillRoll(unit, roller, modifier = modifier)
        return if (psr.passed) {
            // Stood up; activation NOT consumed so the unit may still move this impulse.
            val newState = state.copy(
                units = state.units.map { if (it.id == cmd.unitId) it.copy(isProne = false) else it },
            )
            PhaseOutcome(newState, turn, listOf(UnitStoodUp.Detailed(cmd.unitId, psr, stoodUp = true)))
        } else {
            // Failed to rise; remains prone and its activation is spent.
            PhaseOutcome(state, turn.copy(movement = turn.movement.afterUnitMoved(cmd.unitId)), listOf(UnitStoodUp.Detailed(cmd.unitId, psr, stoodUp = false)))
        }
    }

    /**
     * [validate] has already run [MovementRules.authoritativeDestination] and only
     * reached here because it returned `Legal` — meaning `cmd.destination` is either
     * the stationary shortcut (position/facing match, 0 MP; path not checked, so it's
     * re-derived here rather than trusted) or was proven byte-for-byte equal to the
     * server-computed reachable hex (full equality, including path). Either way the
     * canonical hex is cheap to obtain without re-running the Dijkstra, so it doesn't:
     * one move command spends exactly one Dijkstra, in [validateDestination].
     */
    private fun applyMove(
        cmd: MoveUnit,
        state: GameState,
        turn: TurnState,
    ): PhaseOutcome {
        val unit = state.unitById(cmd.unitId)
        val from = unit.position
        val serverHex: ReachableHex = if (cmd.destination.mpSpent == 0 &&
            cmd.destination.position == unit.position &&
            cmd.destination.facing == unit.facing
        ) {
            MovementRules.stationaryHex(unit)
        } else {
            cmd.destination
        }
        val newState = state.applyMove(cmd.unitId, cmd.mode, serverHex)
        val newTurn = turn.copy(movement = turn.movement.afterUnitMoved(cmd.unitId))
        val event = UnitMoved(
            unitId = cmd.unitId,
            from = from,
            to = serverHex.position,
            finalFacing = serverHex.facing,
            mode = cmd.mode,
            mpSpent = serverHex.mpSpent,
        )
        return PhaseOutcome(newState, newTurn, listOf(event))
    }

    override fun isComplete(turn: TurnState): Boolean =
        turn.movement.sequence.order.isNotEmpty() && turn.movement.isComplete

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
            units = state.units.map { it.copy(movementThisTurn = MovementThisTurn.Stationary) },
        )
        return PhaseOutcome(
            resetState,
            turn.copy(movement = turn.movement.copy(sequence = ImpulseSequence(order))),
            emptyList(),
        )
    }
}
