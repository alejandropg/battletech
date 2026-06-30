package battletech.tactical.attack.physical

import battletech.tactical.attack.ImpulseAttackPhaseHandler
import battletech.tactical.attack.applyLocationDestructionConsequences
import battletech.tactical.attack.applyTwentyDamagePsrs
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommitPhysicalAttackImpulse
import battletech.tactical.session.GameCommand
import battletech.tactical.session.GameEvent
import battletech.tactical.session.PhaseOutcome
import battletech.tactical.session.PhysicalAttacksResolved
import battletech.tactical.session.TurnState
import battletech.tactical.session.UnitFell

/**
 * Physical-attack phase. Same impulse seeding shape as
 * [battletech.tactical.attack.weapon.WeaponAttackPhaseHandler]: declarations
 * accumulate across impulses and, on the final impulse, resolve against the
 * current game state with damage applied (punch/kick to-hit, directional
 * hit-location tables, tonnage-based damage).
 */
public class PhysicalAttackPhaseHandler : ImpulseAttackPhaseHandler() {

    override val phase: TurnPhase = TurnPhase.PHYSICAL_ATTACK

    override fun acceptsCommand(command: GameCommand): Boolean = command is CommitPhysicalAttackImpulse

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

        var newState = applyTorsoFacingsStep(state, cmd.torsoFacings, events)

        var newAttack = turn.attack.recordPhysicalImpulse(cmd.declarations)

        val accumulated = newAttack.physicalDeclarations
        if (newAttack.isComplete && accumulated.isNotEmpty()) {
            val stateBeforeCrits = newState
            val (resolvedState, results) = resolvePhysicalAttacks(accumulated, newState, roller)
            newState = resolvedState
            newAttack = newAttack.clearPhysicalDeclarations()
            events += PhysicalAttacksResolved(results)
            for (result in results) {
                val fallenId = result.fallenUnitId
                val fall = result.fall
                if (fallenId != null && fall != null) {
                    events += UnitFell(unitId = fallenId, fall = fall)
                    // Pilot-hit events from the kick knockdown fall (1 hit + consciousness roll).
                    events += result.fallPilotEvents
                }
            }

            val (stateAfterGyro, gyroEvents) = applyGyroCritEffects(stateBeforeCrits, newState, roller)
            newState = stateAfterGyro
            events += gyroEvents

            // Apply location-destruction consequences (weapon disable, side-torso → arm
            // cascade, leg-destruction falls) for locations newly destroyed this phase.
            val (stateAfterDestruction, destructionEvents) =
                applyLocationDestructionConsequences(stateBeforeCrits, newState, roller)
            newState = stateAfterDestruction
            events += destructionEvents

            // 20-damage PSR: any target that took ≥20 total damage this phase must make a PSR
            // (+1 per full 20, including gyro/leg modifiers). Fail → forced fall + pilot hit.
            val damageByUnit = results
                .groupBy { it.targetId }
                .mapValues { (_, rs) -> rs.sumOf { r -> r.damage.sumOf { s -> s.armorDamage + s.structureDamage } } }
            val (stateAfter20dmg, twentyDmgEvents) = applyTwentyDamagePsrs(newState, damageByUnit, roller)
            newState = stateAfter20dmg
            events += twentyDmgEvents
        }

        return PhaseOutcome(newState, turn.copy(attack = newAttack), events)
    }
}
