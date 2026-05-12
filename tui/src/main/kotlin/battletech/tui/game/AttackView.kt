package battletech.tui.game

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.UnitId
import battletech.tactical.model.FiringArc
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import kotlin.math.ceil

public fun fireArc(unit: CombatUnit, torsoFacing: HexDirection, gameState: GameState): Set<HexCoordinates> =
    FiringArc.forwardArc(unit.position, torsoFacing, gameState.map)

public fun validTargets(unit: CombatUnit, torsoFacing: HexDirection, gameState: GameState): Set<UnitId> {
    val arc = fireArc(unit, torsoFacing, gameState)
    return gameState.units
        .filter { it.owner != unit.owner }
        .filter { it.position in arc }
        .filter { enemy -> hasEligibleWeapon(unit, enemy) }
        .map { it.id }
        .toSet()
}

public fun targetInfos(unit: CombatUnit, torsoFacing: HexDirection, gameState: GameState): List<TargetInfo> {
    val targetIds = validTargets(unit, torsoFacing, gameState)
    return targetIds.mapNotNull { targetId ->
        val target = gameState.unitById(targetId) ?: return@mapNotNull null
        val distance = unit.position.distanceTo(target.position)

        val weapons = unit.weapons.mapIndexed { index, weapon ->
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
                val heatPenalty = heatPenaltyModifier(unit)
                val modifiers = mutableListOf<String>()
                when {
                    distance <= weapon.shortRange -> {}
                    distance <= weapon.mediumRange -> modifiers.add("+2 med")
                    else -> modifiers.add("+4 long")
                }
                if (heatPenalty > 0) modifiers.add("+$heatPenalty heat")

                val chance = TWO_D6_PROBABILITY[unit.gunnerySkill + rangeModifier + heatPenalty] ?: 0
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

private val TWO_D6_PROBABILITY: Map<Int, Int> = mapOf(
    2 to 100, 3 to 97, 4 to 92, 5 to 83, 6 to 72,
    7 to 58, 8 to 42, 9 to 28, 10 to 17, 11 to 8, 12 to 3,
)
