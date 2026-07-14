package battletech.tactical.movement

import battletech.tactical.dice.DiceRoller
import battletech.tactical.heat.movementHeatSource
import battletech.tactical.model.GameState
import battletech.tactical.model.MovementMode
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
import battletech.tactical.unit.cannotStandFromGyroDamage
import battletech.tactical.unit.destroyedLegCount
import battletech.tactical.unit.gyroPsrModifier
import battletech.tactical.unit.legPsrModifier
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
            ?: if (state.unitById(command.unitId).isProne) {
                CommandRejection.UnitProne(command.unitId)
            } else if (command.mode == MovementMode.JUMP &&
                state.unitById(command.unitId).destroyedLegCount() > 0
            ) {
                // Cannot jump with any destroyed leg.
                CommandRejection.LegDestroyed(command.unitId)
            } else if (command.mode == MovementMode.RUN &&
                state.unitById(command.unitId).destroyedLegCount() > 0
            ) {
                // Cannot run with any destroyed leg; hobble (half walk MP) only.
                CommandRejection.LegDestroyed(command.unitId)
            } else {
                validateDestination(command, state)
            }

        is StandUp -> validateActivation(command.unitId, command.playerId, state, turn)
            ?: when {
                !state.unitById(command.unitId).isProne -> CommandRejection.UnitNotProne(command.unitId)
                state.unitById(command.unitId).cannotStandFromGyroDamage() ->
                    CommandRejection.GyroDestroyed(command.unitId)
                else -> null
            }

        else -> null
    }

    /**
     * Server-authoritative destination check. Rejects a [MoveUnit] whose
     * [MoveUnit.destination] is outside the unit's legal reach, or whose
     * path/mpSpent were tampered to differ from the server-computed value.
     *
     * Stationary moves (same position, same facing, 0 MP) are accepted without
     * running the full Dijkstra — they are always legal once the unit passes
     * the prior checks (not prone, not destroyed-leg-run/jump, etc.).
     */
    private fun validateDestination(
        cmd: MoveUnit,
        state: GameState,
    ): CommandRejection? {
        val unit = state.unitById(cmd.unitId) // existence guaranteed by validateActivation
        // Stationary: unit stays at its current position with its current facing, spending 0 MP.
        if (cmd.destination.position == unit.position &&
            cmd.destination.facing == unit.facing &&
            cmd.destination.mpSpent == 0
        ) {
            return null
        }
        val reachabilityMap = ReachabilityCalculator(state.map, state.units).calculate(unit, cmd.mode)
        val serverHex = reachabilityMap.destinations.firstOrNull {
            it.position == cmd.destination.position && it.facing == cmd.destination.facing
        }
        return when {
            serverHex == null -> CommandRejection.DestinationUnreachable(cmd.unitId, cmd.destination.position)
            serverHex != cmd.destination -> CommandRejection.DestinationUnreachable(cmd.unitId, cmd.destination.position)
            else -> null
        }
    }

    private fun validateActivation(
        unitId: battletech.tactical.unit.UnitId,
        playerId: PlayerId,
        state: GameState,
        turn: TurnState,
    ): CommandRejection? {
        if (state.units.none { it.id == unitId }) return CommandRejection.UnknownUnit(unitId)
        val unit = state.unitById(unitId)
        if (unit.owner != playerId) {
            return CommandRejection.NotYourTurn(activePlayer = unit.owner, attemptedBy = playerId)
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
        val modifier = gyroPsrModifier(unit) + legPsrModifier(unit)
        val psr = pilotingSkillRoll(unit, roller, modifier = modifier)
        return if (psr.passed) {
            // Stood up; activation NOT consumed so the unit may still move this impulse.
            val newState = state.copy(
                units = state.units.map { if (it.id == cmd.unitId) it.copy(isProne = false) else it },
            )
            PhaseOutcome(newState, turn, listOf(UnitStoodUp(cmd.unitId, psr, stoodUp = true)))
        } else {
            // Failed to rise; remains prone and its activation is spent.
            PhaseOutcome(state, turn.copy(movement = turn.movement.afterUnitMoved(cmd.unitId)), listOf(UnitStoodUp(cmd.unitId, psr, stoodUp = false)))
        }
    }

    private fun applyMove(
        cmd: MoveUnit,
        state: GameState,
        turn: TurnState,
    ): PhaseOutcome {
        val unit = state.unitById(cmd.unitId)
        // Use the server-authoritative destination — never trust the client value for MP or path.
        val serverHex: ReachableHex = if (cmd.destination.mpSpent == 0 &&
            cmd.destination.position == unit.position &&
            cmd.destination.facing == unit.facing
        ) {
            // Stationary: synthesise a canonical zero-cost hex.
            ReachableHex(position = unit.position, facing = unit.facing, mpSpent = 0, path = emptyList())
        } else {
            ReachabilityCalculator(state.map, state.units)
                .calculate(unit, cmd.mode)
                .destinations
                .first { it.position == cmd.destination.position && it.facing == cmd.destination.facing }
        }
        val from = unit.position
        val hexesMoved = hexesMoved(from, serverHex)
        val heatSource = movementHeatSource(cmd.mode, hexesMoved)
        val movedState = state.moveUnit(cmd.unitId, serverHex)
        val newState = movedState.copy(
            units = movedState.units.map { u ->
                if (u.id == cmd.unitId) {
                    u.copy(
                        movementThisTurn = MovementThisTurn.Moved(cmd.mode, hexesMoved),
                        heatGeneratedThisTurn = u.heatGeneratedThisTurn +
                            listOfNotNull(heatSource),
                    )
                } else {
                    u
                }
            },
        )
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

/** Hexes actually entered along [destination]'s path (turn-in-place steps excluded). */
public fun hexesMoved(from: battletech.tactical.model.HexCoordinates, destination: ReachableHex): Int {
    val positions = listOf(from) + destination.path.map { it.position }
    return positions.zipWithNext().count { (previous, next) -> previous != next }
}
