package battletech.tactical.query

import battletech.tactical.attack.immobileTargetToHitModifier
import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.attack.weapon.WeaponTargetInfo
import battletech.tactical.heat.HeatScale
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.Weapon

internal class WeaponTargeting(private val state: PublicGameState) {

    private val TWO_D6_PROBABILITY: Map<Int, Int> = mapOf(
        2 to 100, 3 to 97, 4 to 92, 5 to 83, 6 to 72,
        7 to 58, 8 to 42, 9 to 28, 10 to 17, 11 to 8, 12 to 3,
    )

    fun fireArc(attackerId: UnitId, torsoFacing: HexDirection): Set<HexCoordinates> {
        val attacker = state.unitById(attackerId) ?: return emptySet()
        return FiringArc.forwardArc(attacker.position, torsoFacing, state.map)
    }

    fun validTargets(attackerId: UnitId, torsoFacing: HexDirection): Set<UnitId> {
        val attacker = state.unitById(attackerId) ?: return emptySet()
        val arc = FiringArc.forwardArc(attacker.position, torsoFacing, state.map)
        return state.units
            .filter { it.owner != attacker.owner }
            .filter { it.position in arc }
            .filter { enemy -> hasEligibleWeapon(attacker, enemy) }
            .map { it.id }
            .toSet()
    }

    fun targetInfos(attackerId: UnitId, torsoFacing: HexDirection): List<TargetInfo> {
        val attacker = state.unitById(attackerId) ?: return emptyList()
        val targetIds = validTargets(attackerId, torsoFacing)
        return targetIds.mapNotNull { targetId ->
            val target = state.unitById(targetId) ?: return@mapNotNull null
            val distance = attacker.position.distanceTo(target.position)

            val weapons = attacker.weapons.mapIndexed { index, weapon ->
                if (!weapon.canEngageAt(distance)) {
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

    private fun hasEligibleWeapon(attacker: CombatUnit, target: CombatUnit): Boolean {
        val distance = attacker.position.distanceTo(target.position)
        return attacker.weapons.any { it.canEngageAt(distance) }
    }

    private fun heatPenaltyModifier(actor: CombatUnit): Int =
        HeatScale.toHitPenalty(actor.currentHeat)
}

private fun Weapon.canEngageAt(distance: Int): Boolean =
    !destroyed &&
        (ammo?.let { it > 0 } != false) &&
        distance <= longRange
