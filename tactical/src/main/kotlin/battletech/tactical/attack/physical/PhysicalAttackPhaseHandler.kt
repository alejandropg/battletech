package battletech.tactical.attack.physical

import battletech.tactical.attack.applyTorsoFacings
import battletech.tactical.attack.weapon.attackOrderFor
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommitPhysicalAttackImpulse
import battletech.tactical.session.GameCommand
import battletech.tactical.session.GameEvent
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.PhaseHandler
import battletech.tactical.session.PhaseOutcome
import battletech.tactical.session.PhysicalAttacksResolved
import battletech.tactical.session.TorsoFacingsApplied
import battletech.tactical.session.TurnState

/**
 * Physical-attack phase. Same impulse seeding shape as
 * [battletech.tactical.attack.weapon.WeaponAttackPhaseHandler]: declarations
 * accumulate across impulses and, on the final impulse, resolve against the
 * current game state with damage applied (punch/kick to-hit, directional
 * hit-location tables, tonnage-based damage).
 */
public class PhysicalAttackPhaseHandler : PhaseHandler {

    override val phase: TurnPhase = TurnPhase.PHYSICAL_ATTACK

    override fun activePlayer(turn: TurnState): PlayerId? =
        if (turn.attackSequence.order.isEmpty() || turn.attackSequence.isComplete) null
        else turn.activeAttackPlayer

    override fun accepts(command: GameCommand, turn: TurnState): Boolean =
        command is CommitPhysicalAttackImpulse &&
            turn.attackSequence.order.isNotEmpty() &&
            !turn.attackSequence.isComplete

    override fun validate(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
    ): CommandRejection? {
        val cmd = command as CommitPhysicalAttackImpulse
        return physicalImpulseViolation(cmd.declarations, state)
            ?.let { CommandRejection.RuleViolation(it) }
    }

    override fun apply(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        val cmd = command as CommitPhysicalAttackImpulse
        val events = mutableListOf<GameEvent>()

        var newState = state
        if (cmd.torsoFacings.isNotEmpty()) {
            newState = newState.applyTorsoFacings(cmd.torsoFacings)
            events += TorsoFacingsApplied(cmd.torsoFacings)
        }

        val accumulated = turn.physicalAttackDeclarations + cmd.declarations
        var newTurn = turn.copy(
            physicalAttackDeclarations = accumulated,
            attackSequence = turn.attackSequence.advance(),
        )

        if (newTurn.attackSequence.isComplete && accumulated.isNotEmpty()) {
            val (resolvedState, results) = resolvePhysicalAttacks(accumulated, newState, roller)
            newState = resolvedState
            newTurn = newTurn.copy(physicalAttackDeclarations = emptyList())
            events += PhysicalAttacksResolved(results)
        }

        return PhaseOutcome(newState, newTurn, events)
    }

    override fun isComplete(turn: TurnState): Boolean =
        turn.attackSequence.order.isNotEmpty() && turn.attackSequence.isComplete

    override fun onEntry(
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        if (turn.attackSequence.order.isNotEmpty() && !turn.attackSequence.isComplete) {
            return PhaseOutcome(state, turn, emptyList())
        }
        val sequence = ImpulseSequence(attackOrderFor(turn.initiative, state))
        val newTurn = turn.copy(attackSequence = sequence)
        return PhaseOutcome(state, newTurn, emptyList())
    }
}
