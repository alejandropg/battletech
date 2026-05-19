package battletech.tactical.session

import battletech.tactical.action.Impulse
import battletech.tactical.action.Initiative
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.attack.resolveAttacks
import battletech.tactical.command.CommitAttackImpulse
import battletech.tactical.command.GameCommand
import battletech.tactical.dice.DiceRoller
import battletech.tactical.event.AttackDeclarationsRecorded
import battletech.tactical.event.AttacksResolved
import battletech.tactical.event.GameEvent
import battletech.tactical.event.TorsoFacingsApplied
import battletech.tactical.model.GameState
import battletech.tactical.model.applyTorsoFacings

/**
 * Weapon-fire attack phase. On entry seeds the attack impulse sequence
 * (loser declares first; alternating impulses). Accepts
 * [CommitAttackImpulse] from the active attack player.
 *
 * Declarations accumulate across impulses; on the final impulse they are
 * resolved against the current game state and damage is applied.
 */
public class WeaponAttackPhaseHandler : PhaseHandler {

    override val phase: TurnPhase = TurnPhase.WEAPON_ATTACK

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
            attackImpulse = null,
            attackSequence = turn.attackSequence.advance(),
        )

        events += AttackDeclarationsRecorded(player = cmd.playerId, count = cmd.declarations.size)

        if (newTurn.attackSequence.isComplete && accumulated.isNotEmpty()) {
            val (resolvedState, results) = resolveAttacks(accumulated, newState, roller)
            newState = resolvedState
            newTurn = newTurn.copy(attackDeclarations = emptyList())
            events += AttacksResolved(results)
        }

        // Between impulses, seed a fresh draft holder for the next active
        // player so the TUI's draft updates have something to write into.
        if (!newTurn.attackSequence.isComplete) {
            newTurn = newTurn.copy(attackImpulse = ImpulseDeclarations(newTurn.activeAttackPlayer))
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
        if (turn.attackSequence.order.isNotEmpty()) return PhaseOutcome(state, turn, emptyList())
        val sequence = ImpulseSequence(attackOrderFor(turn.initiative, state))
        val newTurn = turn.copy(
            attackSequence = sequence,
            attackImpulse = if (sequence.order.isNotEmpty()) {
                ImpulseDeclarations(sequence.activePlayer)
            } else null,
        )
        return PhaseOutcome(state, newTurn, emptyList())
    }
}

internal fun attackOrderFor(initiative: Initiative, state: GameState): List<Impulse> {
    val loser = initiative.loser
    val winner = initiative.winner
    return battletech.tactical.action.calculateAttackOrder(
        loser = loser,
        loserUnitCount = state.unitsOf(loser).size,
        winner = winner,
        winnerUnitCount = state.unitsOf(winner).size,
    )
}
