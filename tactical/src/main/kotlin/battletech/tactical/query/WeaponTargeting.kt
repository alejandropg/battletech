package battletech.tactical.query

import battletech.tactical.attack.WeaponAttackContext
import battletech.tactical.attack.displayLabels
import battletech.tactical.attack.total
import battletech.tactical.attack.weapon.FireWeaponActionDefinition
import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.attack.weapon.WeaponTargetInfo
import battletech.tactical.attack.weaponToHitModifiers
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.cannotFireFromSensorDamage

internal class WeaponTargeting(private val state: PublicGameState) {

    private val definition = FireWeaponActionDefinition()

    fun fireArc(attackerId: UnitId, torsoFacing: HexDirection): Set<HexCoordinates> {
        val attacker = state.unitById(attackerId)
        return FiringArc.forwardArc(attacker.position, torsoFacing, state.map)
    }

    fun validTargets(attackerId: UnitId, torsoFacing: HexDirection): Set<UnitId> {
        val attacker = state.unitById(attackerId)
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
        val attacker = state.unitById(attackerId)
        val targetIds = validTargets(attackerId, torsoFacing)
        return targetIds.mapNotNull { targetId ->
            val target = state.unitById(targetId)
            val distance = attacker.position.distanceTo(target.position)

            val weapons = attacker.weapons.mapIndexed { index, weapon ->
                val context = WeaponAttackContext(
                    actor = attacker,
                    target = target,
                    weapon = weapon,
                    gameState = state,
                )
                if (definition.firstRejection(context) != null) {
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
                        gameState = state,
                    )
                    val targetNumber = (attacker.gunnerySkill + modifiers.total()).coerceAtLeast(2)
                    WeaponTargetInfo(
                        weaponIndex = index,
                        weaponName = weapon.name,
                        targetDiceRoll = targetNumber,
                        damage = weapon.damage,
                        modifiers = modifiers.displayLabels(),
                    )
                }
            }

            if (weapons.none { it.available }) return@mapNotNull null
            TargetInfo(unitId = targetId, unitName = target.name, weapons = weapons)
        }
    }

    /**
     * Returns true if [attacker] has at least one weapon that passes all tactical
     * firing rules ([FireWeaponActionDefinition]) against [target]. Used as the
     * per-target eligibility filter in [validTargets].
     */
    private fun hasEligibleWeapon(attacker: CombatUnit, target: CombatUnit): Boolean =
        attacker.weapons.any { weapon ->
            val context = WeaponAttackContext(
                actor = attacker,
                target = target,
                weapon = weapon,
                gameState = state,
            )
            definition.firstRejection(context) == null
        }
}
