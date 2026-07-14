package battletech.tactical.attack.physical

import battletech.tactical.attack.ImpulseAttackPhaseHandler
import battletech.tactical.attack.PhysicalAttackContext
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

    /**
     * Validates every [PhysicalAttackDeclaration] and torso-facing entry in a
     * [CommitPhysicalAttackImpulse] before [apply] runs. Pure — no dice rolls, no mutation.
     *
     * Checks (in order, returns first violation):
     *  1. Attacker exists and is owned by [CommitPhysicalAttackImpulse.playerId]; target exists,
     *     is not friendly, and is not destroyed (shared [validateTarget]).
     *  2. Tactical legality (adjacency, reach, movement, attacker-prone, heat) via
     *     [PunchActionDefinition.firstRejection] or [KickActionDefinition.firstRejection].
     *  3. Each torso-facing unit exists, is owned by the command player, and the
     *     requested facing is a legal ±1 twist from the unit's leg facing (shared [validateTorsoFacings]).
     *  4. Per-turn physical-attack limits (no punch+kick same turn, no limb reuse)
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
            validateTarget(decl.attackerId, decl.targetId, cmd.playerId, state)?.let { return it }

            val attacker = state.unitById(decl.attackerId)
            val target = state.unitById(decl.targetId)
            val definition = when (decl.kind) {
                is PhysicalAttackKind.Punch -> PunchActionDefinition()
                is PhysicalAttackKind.Kick -> KickActionDefinition()
            }
            val context = PhysicalAttackContext(actor = attacker, target = target, gameState = state)
            definition.firstRejection(context)?.let { rejection ->
                return CommandRejection.RuleViolation(rejection)
            }
        }

        validateTorsoFacings(cmd, state)?.let { return it }

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

            val (stateAfterTail, tailEvents) =
                resolveVolleyTail(stateBeforeCrits, newState, results.filterIsInstance<PhysicalAttackResult.Hit>(), roller)
            newState = stateAfterTail
            events += tailEvents
        }

        return PhaseOutcome(newState, turn.copy(attack = newAttack), events)
    }
}
