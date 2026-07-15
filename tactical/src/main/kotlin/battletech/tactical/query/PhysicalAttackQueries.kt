package battletech.tactical.query

import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.PhysicalAttackContext
import battletech.tactical.attack.physical.KickActionDefinition
import battletech.tactical.attack.physical.PhysicalAttackKind
import battletech.tactical.attack.physical.PunchActionDefinition
import battletech.tactical.attack.physical.Side
import battletech.tactical.attack.physical.kickDamage
import battletech.tactical.attack.physical.physicalToHitTargetNumber
import battletech.tactical.attack.physical.punchDamage
import battletech.tactical.model.MechLocation
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId

/**
 * Physical-attack options over a per-viewer [PlayerGameState]; see [WeaponTargeting] for the
 * actor/target typing rationale. The attacker is resolved via [PlayerGameState.ownUnitById]
 * (piloting skill, heat and limb internal structure all feed these options); adjacent
 * enemies stay [VisibleUnit].
 */
internal class PhysicalAttackQueries(private val state: PlayerGameState) {

    fun physicalAttackOptions(attackerId: UnitId): List<PhysicalAttackOption> {
        val attacker = state.ownUnitById(attackerId)
        val punchDef = PunchActionDefinition()
        val kickDef = KickActionDefinition()

        val adjacentEnemies = state.units
            .filter { it.owner != attacker.owner }
            .filter { !it.isDestroyed }
            .filter { attacker.position.distanceTo(it.position) == 1 }

        return adjacentEnemies.flatMap { enemy ->
            val context = PhysicalAttackContext(actor = attacker, map = state.map, target = enemy)

            val punchReasons = unsatisfiedReasons(punchDef.rules, context)
            // C4: the only to-hit-number math here is the shared predictor
            // (battletech.tactical.attack.physical.physicalToHitTargetNumber), also used by
            // PhysicalAttackResolution — read and apply can't drift.
            val punchTargetNumber = physicalToHitTargetNumber(attacker, enemy, PhysicalAttackKind.Punch(Side.LEFT), state.map)
            val punchOptions = listOf(Side.LEFT, Side.RIGHT).map { arm ->
                val limbReasons = punchReasons + limbDestroyedReason(armStructure(attacker, arm), attackerId)
                PhysicalAttackOption(
                    targetId = enemy.id,
                    targetName = enemy.name,
                    kind = PhysicalAttackKind.Punch(arm),
                    label = "Punch (${arm.name.lowercase()} arm)",
                    available = limbReasons.isEmpty(),
                    targetDiceRoll = punchTargetNumber,
                    expectedDamage = punchDamage(attacker),
                    unavailableReasons = limbReasons,
                )
            }

            val kickReasons = unsatisfiedReasons(kickDef.rules, context)
            val kickTargetNumber = physicalToHitTargetNumber(attacker, enemy, PhysicalAttackKind.Kick(Side.RIGHT), state.map)
            // Kick uses whichever leg is intact (prefer right); the kicking leg only
            // matters for the attacker's own fall on a miss.
            val kickLeg = if (legStructure(attacker, Side.RIGHT) > 0) Side.RIGHT else Side.LEFT
            val kickLegReasons = kickReasons + limbDestroyedReason(legStructure(attacker, kickLeg), attackerId)
            val kickOption = PhysicalAttackOption(
                targetId = enemy.id,
                targetName = enemy.name,
                kind = PhysicalAttackKind.Kick(kickLeg),
                label = "Kick",
                available = kickLegReasons.isEmpty(),
                targetDiceRoll = kickTargetNumber,
                expectedDamage = kickDamage(attacker),
                unavailableReasons = kickLegReasons,
            )

            punchOptions + kickOption
        }
    }

    /**
     * All unsatisfied rule reasons (not just the first) — [PhysicalAttackOption.unavailableReasons]
     * displays every reason an option is unavailable, so this evaluates [rules] directly rather
     * than [battletech.tactical.attack.AttackDefinition.firstRejection], which short-circuits on
     * the first hit. Not a to-hit-number duplication: no target-number math happens here.
     */
    private fun unsatisfiedReasons(
        rules: List<AttackRule<PhysicalAttackContext>>,
        context: PhysicalAttackContext,
    ): List<battletech.tactical.session.RuleRejection> =
        rules.mapNotNull { (it.evaluate(context) as? RuleResult.Unsatisfied)?.reason }

    private fun limbDestroyedReason(
        structure: Int,
        attackerId: UnitId,
    ): List<battletech.tactical.session.RuleRejection> =
        if (structure > 0) emptyList() else listOf(battletech.tactical.session.RuleRejection.LimbDestroyed(attackerId))

    private fun armStructure(unit: CombatUnit, arm: Side): Int =
        unit.internalStructure.at(if (arm == Side.LEFT) MechLocation.LEFT_ARM else MechLocation.RIGHT_ARM)

    private fun legStructure(unit: CombatUnit, leg: Side): Int =
        unit.internalStructure.at(if (leg == Side.LEFT) MechLocation.LEFT_LEG else MechLocation.RIGHT_LEG)
}
