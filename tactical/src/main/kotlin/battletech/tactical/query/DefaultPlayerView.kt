package battletech.tactical.query

import battletech.tactical.attack.PhysicalAttackContext
import battletech.tactical.attack.physical.KickActionDefinition
import battletech.tactical.attack.physical.PhysicalAttackKind
import battletech.tactical.attack.physical.PunchActionDefinition
import battletech.tactical.attack.physical.Side
import battletech.tactical.attack.physical.kickDamage
import battletech.tactical.attack.physical.physicalToHitTargetNumber
import battletech.tactical.attack.physical.punchDamage
import battletech.tactical.attack.physical.twoD6AtLeastProbability
import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.attack.weapon.WeaponTargetInfo
import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.immobileTargetToHitModifier
import battletech.tactical.heat.HeatScale
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.ReachabilityCalculator
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId

public class DefaultPlayerView(
    override val playerId: PlayerId,
    override val state: PublicGameState,
) : PlayerView {

    override fun legalMovementsFor(unitId: UnitId): List<ReachabilityMap> {
        val unit = state.unitById(unitId) ?: return emptyList()
        if (unit.isShutdown) return emptyList()
        val calculator = ReachabilityCalculator(state.map, state.units)
        return buildList {
            if (unit.walkingMP > 0) add(calculator.calculate(unit, MovementMode.WALK))
            if (unit.runningMP > 0) add(calculator.calculate(unit, MovementMode.RUN))
            if (unit.jumpMP > 0) add(calculator.calculate(unit, MovementMode.JUMP))
        }
    }

    override fun fireArc(attackerId: UnitId, torsoFacing: HexDirection): Set<HexCoordinates> {
        val attacker = state.unitById(attackerId) ?: return emptySet()
        return FiringArc.forwardArc(attacker.position, torsoFacing, state.map)
    }

    override fun validTargets(attackerId: UnitId, torsoFacing: HexDirection): Set<UnitId> {
        val attacker = state.unitById(attackerId) ?: return emptySet()
        val arc = FiringArc.forwardArc(attacker.position, torsoFacing, state.map)
        return state.units
            .filter { it.owner != attacker.owner }
            .filter { it.position in arc }
            .filter { enemy -> hasEligibleWeapon(attacker, enemy) }
            .map { it.id }
            .toSet()
    }

    override fun targetInfos(attackerId: UnitId, torsoFacing: HexDirection): List<TargetInfo> {
        val attacker = state.unitById(attackerId) ?: return emptyList()
        val targetIds = validTargets(attackerId, torsoFacing)
        return targetIds.mapNotNull { targetId ->
            val target = state.unitById(targetId) ?: return@mapNotNull null
            val distance = attacker.position.distanceTo(target.position)

            val weapons = attacker.weapons.mapIndexed { index, weapon ->
                val inRange = !weapon.destroyed &&
                    (weapon.ammo?.let { it > 0 } != false) &&
                    distance <= weapon.longRange

                if (!inRange) {
                    WeaponTargetInfo(
                        weaponIndex = index,
                        weaponName = weapon.name,
                        successChance = 0,
                        damage = weapon.damage,
                        modifiers = emptyList(),
                        available = false,
                    )
                } else {
                    val rangeModifier = when {
                        distance <= weapon.shortRange -> 0
                        distance <= weapon.mediumRange -> 2
                        else -> 4
                    }
                    val heatPenalty = heatPenaltyModifier(attacker)
                    val immobileModifier = immobileTargetToHitModifier(target)
                    val modifiers = mutableListOf<String>()
                    when {
                        distance <= weapon.shortRange -> {}
                        distance <= weapon.mediumRange -> modifiers.add("+2 med")
                        else -> modifiers.add("+4 long")
                    }
                    if (heatPenalty > 0) modifiers.add("+$heatPenalty heat")
                    if (immobileModifier != 0) modifiers.add("$immobileModifier immobile")

                    val targetNumber = (attacker.gunnerySkill + rangeModifier + heatPenalty + immobileModifier)
                        .coerceAtLeast(2)
                    val chance = TWO_D6_PROBABILITY[targetNumber] ?: 0
                    WeaponTargetInfo(
                        weaponIndex = index,
                        weaponName = weapon.name,
                        successChance = chance,
                        damage = weapon.damage,
                        modifiers = modifiers,
                        available = true,
                    )
                }
            }

            if (weapons.none { it.available }) return@mapNotNull null
            TargetInfo(unitId = targetId, unitName = target.name, weapons = weapons)
        }
    }

    override fun physicalAttackOptions(attackerId: UnitId): List<PhysicalAttackOption> {
        val attacker = state.unitById(attackerId) ?: return emptyList()
        val punchDef = PunchActionDefinition()
        val kickDef = KickActionDefinition()

        val adjacentEnemies = state.units
            .filter { it.owner != attacker.owner }
            .filter { attacker.position.distanceTo(it.position) == 1 }

        return adjacentEnemies.flatMap { enemy ->
            val context = PhysicalAttackContext(actor = attacker, gameState = state, target = enemy)

            val punchReasons = unsatisfiedReasons(punchDef.rules, context)
            val punchChance = twoD6AtLeastProbability(
                physicalToHitTargetNumber(attacker, enemy, PhysicalAttackKind.Punch(Side.LEFT), state),
            )
            val punchOptions = listOf(Side.LEFT, Side.RIGHT).map { arm ->
                val limbReasons = punchReasons + limbDestroyedReason(armStructure(attacker, arm), attackerId)
                PhysicalAttackOption(
                    targetId = enemy.id,
                    targetName = enemy.name,
                    kind = PhysicalAttackKind.Punch(arm),
                    label = "Punch (${arm.name.lowercase()} arm)",
                    available = limbReasons.isEmpty(),
                    successChance = punchChance,
                    expectedDamage = punchDamage(attacker),
                    unavailableReasons = limbReasons,
                )
            }

            val kickReasons = unsatisfiedReasons(kickDef.rules, context)
            val kickChance = twoD6AtLeastProbability(
                physicalToHitTargetNumber(attacker, enemy, PhysicalAttackKind.Kick(Side.RIGHT), state),
            )
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
                expectedDamage = kickDamage(attacker),
                unavailableReasons = kickLegReasons,
            )

            punchOptions + kickOption
        }
    }

    override fun resolveTargetPositions(targetIds: Set<UnitId>): Set<HexCoordinates> =
        targetIds.mapNotNull { state.unitById(it)?.position }.toSet()

    override fun publicUnit(unitId: UnitId): PublicUnit? {
        val unit = state.unitById(unitId) ?: return null
        return PublicUnit(
            id = unit.id,
            owner = unit.owner,
            name = unit.name,
            walkingMP = unit.walkingMP,
            runningMP = unit.runningMP,
            jumpMP = unit.jumpMP,
            armor = unit.armor,
            weapons = unit.weapons.map { PublicWeapon(name = it.name) },
        )
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

    private fun hasEligibleWeapon(attacker: CombatUnit, target: CombatUnit): Boolean {
        val distance = attacker.position.distanceTo(target.position)
        return attacker.weapons.any { weapon ->
            !weapon.destroyed &&
                (weapon.ammo?.let { it > 0 } != false) &&
                distance <= weapon.longRange
        }
    }

    private fun heatPenaltyModifier(actor: CombatUnit): Int =
        HeatScale.toHitPenalty(actor.currentHeat)

    private companion object {
        val TWO_D6_PROBABILITY: Map<Int, Int> = mapOf(
            2 to 100, 3 to 97, 4 to 92, 5 to 83, 6 to 72,
            7 to 58, 8 to 42, 9 to 28, 10 to 17, 11 to 8, 12 to 3,
        )
    }
}
