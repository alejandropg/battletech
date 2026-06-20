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
import battletech.tactical.attack.physical.twoD6AtLeastProbability
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId

internal class PhysicalAttackQueries(private val state: PublicGameState) {

    fun physicalAttackOptions(attackerId: UnitId): List<PhysicalAttackOption> {
        val attacker = state.unitById(attackerId) ?: return emptyList()
        val punchDef = PunchActionDefinition()
        val kickDef = KickActionDefinition()

        val adjacentEnemies = state.units
            .filter { it.owner != attacker.owner }
            .filter { attacker.position.distanceTo(it.position) == 1 }

        return adjacentEnemies.flatMap { enemy ->
            val context = PhysicalAttackContext(actor = attacker, gameState = state, target = enemy)

            val punchReasons = unsatisfiedReasons(punchDef.rules, context)
            val punchTargetNumber = physicalToHitTargetNumber(attacker, enemy, PhysicalAttackKind.Punch(Side.LEFT), state)
            val punchChance = twoD6AtLeastProbability(punchTargetNumber)
            val punchOptions = listOf(Side.LEFT, Side.RIGHT).map { arm ->
                val limbReasons = punchReasons + limbDestroyedReason(armStructure(attacker, arm), attackerId)
                PhysicalAttackOption(
                    targetId = enemy.id,
                    targetName = enemy.name,
                    kind = PhysicalAttackKind.Punch(arm),
                    label = "Punch (${arm.name.lowercase()} arm)",
                    available = limbReasons.isEmpty(),
                    successChance = punchChance,
                    targetDiceRoll = punchTargetNumber,
                    expectedDamage = punchDamage(attacker),
                    unavailableReasons = limbReasons,
                )
            }

            val kickReasons = unsatisfiedReasons(kickDef.rules, context)
            val kickTargetNumber = physicalToHitTargetNumber(attacker, enemy, PhysicalAttackKind.Kick(Side.RIGHT), state)
            val kickChance = twoD6AtLeastProbability(kickTargetNumber)
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
                successChance = kickChance,
                targetDiceRoll = kickTargetNumber,
                expectedDamage = kickDamage(attacker),
                unavailableReasons = kickLegReasons,
            )

            punchOptions + kickOption
        }
    }

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
        if (arm == Side.LEFT) unit.internalStructure.leftArm else unit.internalStructure.rightArm

    private fun legStructure(unit: CombatUnit, leg: Side): Int =
        if (leg == Side.LEFT) unit.internalStructure.leftLeg else unit.internalStructure.rightLeg
}
