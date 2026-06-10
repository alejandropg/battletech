package battletech.tactical.attack.weapon

import battletech.tactical.attack.ImpulseAttackPhaseHandler
import battletech.tactical.attack.resolveAttacks
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.AttackDeclarationsRecorded
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.applyWeaponHeat
import battletech.tactical.session.CommitAttackImpulse
import battletech.tactical.session.GameCommand
import battletech.tactical.session.GameEvent
import battletech.tactical.session.PhaseOutcome
import battletech.tactical.session.TurnState

/**
 * Weapon-fire attack phase. On entry seeds the attack impulse sequence
 * (loser declares first; alternating impulses). Accepts
 * [CommitAttackImpulse] from the active attack player.
 *
 * Declarations accumulate across impulses; on the final impulse they are
 * resolved against the current game state and damage is applied.
 */
public class WeaponAttackPhaseHandler : ImpulseAttackPhaseHandler() {

    override val phase: TurnPhase = TurnPhase.WEAPON_ATTACK

    override fun acceptsCommand(command: GameCommand): Boolean = command is CommitAttackImpulse

    override fun apply(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        val cmd = command as CommitAttackImpulse
        val events = mutableListOf<GameEvent>()

        var newState = applyTorsoFacingsStep(state, cmd.torsoFacings, events)

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
}
