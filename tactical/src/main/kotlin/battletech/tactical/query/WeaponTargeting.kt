package battletech.tactical.query

import battletech.tactical.attack.nonZero
import battletech.tactical.attack.total
import battletech.tactical.attack.weaponToHitModifiers
import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.attack.weapon.WeaponTargetInfo
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.Weapon
import battletech.tactical.unit.cannotFireFromSensorDamage

internal class WeaponTargeting(private val state: PublicGameState) {

    fun fireArc(attackerId: UnitId, torsoFacing: HexDirection): Set<HexCoordinates> {
        val attacker = state.unitById(attackerId) ?: return emptySet()
        return FiringArc.forwardArc(attacker.position, torsoFacing, state.map)
    }

    fun validTargets(attackerId: UnitId, torsoFacing: HexDirection): Set<UnitId> {
        val attacker = state.unitById(attackerId) ?: return emptySet()
        // An unconscious pilot cannot act this turn (Stage 7) — same actor-eligibility
        // treatment as a sensor-blinded unit. Unconscious units remain valid TARGETS
        // (see the `!it.isDestroyed` filter below, which deliberately does not also
        // check isPilotConscious).
        if (!attacker.isPilotConscious) return emptySet()
        if (attacker.cannotFireFromSensorDamage()) return emptySet()
        val arc = FiringArc.forwardArc(attacker.position, torsoFacing, state.map)
        return state.units
            .filter { it.owner != attacker.owner }
            .filter { !it.isDestroyed }
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
                if (!weapon.canEngageAt(distance, attacker)) {
                    WeaponTargetInfo(
                        weaponIndex = index,
                        weaponName = weapon.name,
                        targetDiceRoll = 13,
                        damage = weapon.damage,
                        modifiers = emptyList(),
                        available = false,
                    )
                } else {
                    val modifiers = weaponToHitModifiers(
                        attacker = attacker,
                        target = target,
                        weapon = weapon,
                        distance = distance,
                        isPrimaryTarget = true,
                    )
                    val targetNumber = (attacker.gunnerySkill + modifiers.total()).coerceAtLeast(2)
                    val modifierLabels = modifiers.nonZero().map { (label, amount) ->
                        "${if (amount > 0) "+" else ""}$amount $label"
                    }
                    WeaponTargetInfo(
                        weaponIndex = index,
                        weaponName = weapon.name,
                        targetDiceRoll = targetNumber,
                        damage = weapon.damage,
                        modifiers = modifierLabels,
                    )
                }
            }

            if (weapons.none { it.available }) return@mapNotNull null
            TargetInfo(unitId = targetId, unitName = target.name, weapons = weapons)
        }
    }

    private fun hasEligibleWeapon(attacker: CombatUnit, target: CombatUnit): Boolean {
        val distance = attacker.position.distanceTo(target.position)
        return attacker.weapons.any { it.canEngageAt(distance, attacker) }
    }
}

private fun Weapon.canEngageAt(distance: Int, attacker: CombatUnit): Boolean =
    !destroyed &&
        hasAmmoRemaining(attacker) &&
        distance <= longRange

private fun Weapon.hasAmmoRemaining(attacker: CombatUnit): Boolean {
    val type = ammoType ?: return true
    return attacker.criticalLayout.ammoBins().any { it.third.type == type && it.third.shots > 0 }
}
