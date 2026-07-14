package battletech.tactical.attack.physical

import battletech.tactical.attack.ImpulseAttackPhaseHandler
import battletech.tactical.attack.PhysicalAttackContext
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
import battletech.tactical.session.RuleRejection
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

    /**
     * Validates every [PhysicalAttackDeclaration] and torso-facing entry in a
     * [CommitPhysicalAttackImpulse] before [apply] runs. Pure — no dice rolls, no mutation.
     *
     * Checks (in order, returns first violation):
     *  1. Attacker exists and is owned by [CommitPhysicalAttackImpulse.playerId].
     *  2. Target exists, is not friendly, and is not destroyed.
     *  3. Tactical legality (adjacency, reach, movement, attacker-prone, heat) via
     *     [PunchActionDefinition.firstRejection] or [KickActionDefinition.firstRejection].
     *  4. Each torso-facing unit exists, is owned by the command player, and the
     *     requested facing is a legal ±1 twist from the unit's leg facing.
     *  5. Per-turn physical-attack limits (no punch+kick same turn, no limb reuse)
     *     via [physicalImpulseViolation].
     *
     * The active-player check (session-level) is NOT repeated here — [BattleSession]
     * already rejects mismatched active players before calling [validate].
     */
    override fun validate(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
    ): CommandRejection? {
        val cmd = command as CommitPhysicalAttackImpulse

        for (decl in cmd.declarations) {
            val attacker = state.unitById(decl.attackerId)

            if (attacker.owner != cmd.playerId) {
                return CommandRejection.NotYourTurn(
                    activePlayer = attacker.owner,
                    attemptedBy = cmd.playerId,
                )
            }

            val target = state.unitById(decl.targetId)

            if (target.owner == attacker.owner) {
                return CommandRejection.FriendlyFire(decl.targetId)
            }

            if (target.isDestroyed) {
                return CommandRejection.RuleViolation(RuleRejection.TargetDestroyed)
            }

            val definition = when (decl.kind) {
                is PhysicalAttackKind.Punch -> PunchActionDefinition()
                is PhysicalAttackKind.Kick -> KickActionDefinition()
            }
            val context = PhysicalAttackContext(actor = attacker, target = target, gameState = state)
            definition.firstRejection(context)?.let { rejection ->
                return CommandRejection.RuleViolation(rejection)
            }
        }

        for ((unitId, newFacing) in cmd.torsoFacings) {
            val unit = state.unitById(unitId)

            if (unit.owner != cmd.playerId) {
                return CommandRejection.NotYourTurn(
                    activePlayer = unit.owner,
                    attemptedBy = cmd.playerId,
                )
            }

            if (unit.facing.turnCostTo(newFacing) > 1) {
                return CommandRejection.IllegalTorsoTwist(unitId, newFacing)
            }
        }

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
                when (val knockdown = result.knockdown) {
                    is Knockdown.Fell -> {
                        events += UnitFell(unitId = knockdown.unitId, fall = knockdown.fall)
                        // Pilot-hit events from the kick knockdown fall (1 hit + consciousness roll).
                        events += knockdown.pilotEvents
                    }
                    is Knockdown.Resisted, Knockdown.None -> Unit
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
                .mapValues { (_, rs) ->
                    rs.filterIsInstance<PhysicalAttackResult.Hit>()
                        .sumOf { r -> r.damage.sumOf { s -> s.armorDamage + s.structureDamage } }
                }
            val (stateAfter20dmg, twentyDmgEvents) = applyTwentyDamagePsrs(newState, damageByUnit, roller)
            newState = stateAfter20dmg
            events += twentyDmgEvents
        }

        return PhaseOutcome(newState, turn.copy(attack = newAttack), events)
    }
}
