package battletech.tactical.attack.weapon

import battletech.tactical.attack.applyTorsoFacings
import battletech.tactical.attack.resolveAttacks
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.AttackDeclarationsRecorded
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.applyWeaponHeat
import battletech.tactical.session.CommitAttackImpulse
import battletech.tactical.session.GameCommand
import battletech.tactical.session.GameEvent
import battletech.tactical.session.Impulse
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.Initiative
import battletech.tactical.session.PhaseHandler
import battletech.tactical.session.PhaseOutcome
import battletech.tactical.session.TorsoFacingsApplied
import battletech.tactical.session.TurnState
import battletech.tactical.session.calculateAttackOrder

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

        // Fired weapons generate heat regardless of whether they hit; record it
        // on the attacker now so the Heat Phase folds it in.
        newState = newState.applyWeaponHeat(cmd.declarations)

        val accumulated = turn.attackDeclarations + cmd.declarations
        var newTurn = turn.copy(
            attackDeclarations = accumulated,
            attackSequence = turn.attackSequence.advance(),
        )

        events += AttackDeclarationsRecorded(player = cmd.playerId, declarations = cmd.declarations)

        if (newTurn.attackSequence.isComplete && accumulated.isNotEmpty()) {
            val (resolvedState, results) = resolveAttacks(accumulated, newState, roller)
            newState = resolvedState
            newTurn = newTurn.copy(attackDeclarations = emptyList())
            events += AttacksResolved(results)
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

internal fun attackOrderFor(initiative: Initiative, state: GameState): List<Impulse> {
    val loser = initiative.loser
    val winner = initiative.winner
    // Shutdown 'Mechs can't fire, so they don't take an impulse slot.
    return calculateAttackOrder(
        loser = loser,
        loserUnitCount = state.activeUnitsOf(loser).size,
        winner = winner,
        winnerUnitCount = state.activeUnitsOf(winner).size,
    )
}
