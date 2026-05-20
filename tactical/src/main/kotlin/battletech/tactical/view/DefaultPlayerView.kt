package battletech.tactical.view

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import battletech.tactical.model.FiringArc
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.movement.MovementMode
import battletech.tactical.movement.ReachabilityCalculator
import battletech.tactical.movement.ReachabilityMap
import kotlin.math.ceil

public class DefaultPlayerView(
    override val playerId: PlayerId,
    override val state: PublicGameState,
) : PlayerView {

    override fun legalMovementsFor(unitId: UnitId): List<ReachabilityMap> {
        val unit = state.unitById(unitId) ?: return emptyList()
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
                    val modifiers = mutableListOf<String>()
                    when {
                        distance <= weapon.shortRange -> {}
                        distance <= weapon.mediumRange -> modifiers.add("+2 med")
                        else -> modifiers.add("+4 long")
                    }
                    if (heatPenalty > 0) modifiers.add("+$heatPenalty heat")

                    val chance = TWO_D6_PROBABILITY[attacker.gunnerySkill + rangeModifier + heatPenalty] ?: 0
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

    override fun resolveTargetPositions(targetIds: Set<UnitId>): Set<HexCoordinates> =
        targetIds.mapNotNull { state.unitById(it)?.position }.toSet()

    private fun hasEligibleWeapon(attacker: CombatUnit, target: CombatUnit): Boolean {
        val distance = attacker.position.distanceTo(target.position)
        return attacker.weapons.any { weapon ->
            !weapon.destroyed &&
                (weapon.ammo?.let { it > 0 } != false) &&
                distance <= weapon.longRange
        }
    }

    private fun heatPenaltyModifier(actor: CombatUnit): Int {
        val excessHeat = actor.currentHeat - actor.heatSinkCapacity
        return if (excessHeat <= 0) 0 else ceil(excessHeat / 3.0).toInt()
    }

    private companion object {
        val TWO_D6_PROBABILITY: Map<Int, Int> = mapOf(
            2 to 100, 3 to 97, 4 to 92, 5 to 83, 6 to 72,
            7 to 58, 8 to 42, 9 to 28, 10 to 17, 11 to 8, 12 to 3,
        )
    }
}
