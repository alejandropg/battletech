package battletech.tactical.attack.physical

import battletech.tactical.attack.applyTorsoFacings
import battletech.tactical.attack.weapon.attackOrderFor
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.AttackDeclarationsRecorded
import battletech.tactical.session.CommitAttackImpulse
import battletech.tactical.session.GameCommand
import battletech.tactical.session.GameEvent
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.PhaseHandler
import battletech.tactical.session.PhaseOutcome
import battletech.tactical.session.TorsoFacingsApplied
import battletech.tactical.session.TurnState

/**
 * Physical-attack phase. Same impulse seeding shape as
 * [battletech.tactical.attack.weapon.WeaponAttackPhaseHandler], but the current engine doesn't resolve
 * physical damage — declarations are recorded and dropped at the end of
 * the phase, matching the pre-PR6 TUI behaviour.
 */
public class PhysicalAttackPhaseHandler : PhaseHandler {

    override val phase: TurnPhase = TurnPhase.PHYSICAL_ATTACK

    override fun activePlayer(turn: TurnState): PlayerId? =
        if (turn.attackSequence.order.isEmpty() || turn.attackSequence.isComplete) null
        else turn.activeAttackPlayer

    override fun accepts(command: GameCommand, turn: TurnState): Boolean =
        command is CommitAttackImpulse &&
            turn.attackSequence.order.isNotEmpty() &&
            !turn.attackSequence.isComplete

    override fun apply(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        val cmd = command as CommitAttackImpulse
        val events = mutableListOf<GameEvent>()

        var newState = state
        if (cmd.torsoFacings.isNotEmpty()) {
            newState = newState.applyTorsoFacings(cmd.torsoFacings)
            events += TorsoFacingsApplied(cmd.torsoFacings)
        }

        val accumulated = turn.attackDeclarations + cmd.declarations
        var newTurn = turn.copy(
            attackDeclarations = accumulated,
            attackSequence = turn.attackSequence.advance(),
        )
        events += AttackDeclarationsRecorded(player = cmd.playerId, count = cmd.declarations.size)

        if (newTurn.attackSequence.isComplete) {
            newTurn = newTurn.copy(attackDeclarations = emptyList())
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
