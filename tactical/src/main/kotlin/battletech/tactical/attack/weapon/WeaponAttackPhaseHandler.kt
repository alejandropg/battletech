package battletech.tactical.attack.weapon

import battletech.tactical.attack.ImpulseAttackPhaseHandler
import battletech.tactical.attack.resolveAttacksWithCrits
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

        var newAttack = turn.attack.recordWeaponImpulse(cmd.declarations)

        events += AttackDeclarationsRecorded(player = cmd.playerId, declarations = cmd.declarations)

        val accumulated = newAttack.weaponDeclarations
        if (newAttack.isComplete && accumulated.isNotEmpty()) {
            val stateBeforeCrits = newState
            val (resolvedState, results, criticalHits) = resolveAttacksWithCrits(accumulated, newState, roller)
            newState = resolvedState
            newAttack = newAttack.clearWeaponDeclarations()
            events += AttacksResolved(results)
            events += criticalHits

            val (stateAfterGyro, gyroEvents) = applyGyroCritEffects(stateBeforeCrits, newState, roller)
            newState = stateAfterGyro
            events += gyroEvents
        }

        return PhaseOutcome(newState, turn.copy(attack = newAttack), events)
    }
}
