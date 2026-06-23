package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.heat.HeatScale
import battletech.tactical.model.GameState
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.InternalStructureLayout

public fun resolveAttacks(
    declarations: List<AttackDeclaration>,
    gameState: GameState,
    roller: DiceRoller,
): Pair<GameState, List<AttackResult>> {
    // All attacks resolve against the original state (simultaneous resolution)
    val results = declarations.map { declaration ->
        resolveOneAttack(declaration, gameState, roller)
    }

    // Apply all damage to get the final state
    var updatedState = gameState
    val finalResults = results.map { result ->
        if (result.hit && result.hitLocation != null) {
            val target = updatedState.unitById(result.targetId)
            if (target == null) {
                result
            } else {
                val resolution = resolveDamage(target, result.hitLocation, result.damageApplied)
                updatedState = updatedState.copy(
                    units = updatedState.units.map { if (it.id == result.targetId) resolution.unit else it },
                )
                result.copy(damage = resolution.steps)
            }
        } else {
            result
        }
    }

    return updatedState to finalResults
}

public fun applyDamage(
    unit: CombatUnit,
    location: HitLocation,
    damage: Int,
    useRearArmor: Boolean = false,
): CombatUnit = resolveDamage(unit, location, damage, useRearArmor).unit

/**
 * Applies [damage] to [location] on [unit]: armor first, then internal structure (IS).
 * If the location's IS is destroyed (reaches 0), any excess damage transfers inward
 * per the blow-through rules (`docs/rules/armor-damage.md` §5), hitting the new
 * location's armor first and preserving [useRearArmor]. Head and Center Torso do not
 * transfer; excess damage there is dropped.
 */
public fun resolveDamage(
    unit: CombatUnit,
    location: HitLocation,
    damage: Int,
    useRearArmor: Boolean = false,
): DamageResolution {
    if (damage <= 0) return DamageResolution(unit, emptyList())

    val currentArmor = getArmor(unit.armor, location, useRearArmor)
    val armorAfter = (currentArmor - damage).coerceAtLeast(0)
    val armorDamage = minOf(currentArmor, damage)
    val toStructure = (damage - currentArmor).coerceAtLeast(0)
    val newArmor = setArmor(unit.armor, location, armorAfter, useRearArmor)

    if (toStructure == 0) {
        val updatedUnit = unit.copy(armor = newArmor)
        val step = LocationDamage(
            location = location,
            armorDamage = armorDamage,
            structureDamage = 0,
            destroyed = false,
        )
        return DamageResolution(updatedUnit, listOf(step))
    }

    val currentIS = getInternalStructure(unit.internalStructure, location)
    val isAfter = (currentIS - toStructure).coerceAtLeast(0)
    val structureDamage = minOf(currentIS, toStructure)
    val destroyed = isAfter == 0
    val excess = (toStructure - currentIS).coerceAtLeast(0)
    val newIS = setInternalStructure(unit.internalStructure, location, isAfter)
    val updatedUnit = unit.copy(armor = newArmor, internalStructure = newIS)

    val step = LocationDamage(
        location = location,
        armorDamage = armorDamage,
        structureDamage = structureDamage,
        destroyed = destroyed,
    )

    val nextLocation = transferTarget(location)
    return if (excess > 0 && nextLocation != null) {
        val inner = resolveDamage(updatedUnit, nextLocation, excess, useRearArmor)
        DamageResolution(inner.unit, listOf(step) + inner.steps)
    } else {
        DamageResolution(updatedUnit, listOf(step))
    }
}

private fun transferTarget(location: HitLocation): HitLocation? = when (location) {
    HitLocation.LEFT_ARM, HitLocation.LEFT_LEG -> HitLocation.LEFT_TORSO
    HitLocation.RIGHT_ARM, HitLocation.RIGHT_LEG -> HitLocation.RIGHT_TORSO
    HitLocation.LEFT_TORSO, HitLocation.RIGHT_TORSO -> HitLocation.CENTER_TORSO
    HitLocation.HEAD, HitLocation.CENTER_TORSO -> null
}

private fun resolveOneAttack(
    declaration: AttackDeclaration,
    gameState: GameState,
    roller: DiceRoller,
): AttackResult {
    val attacker = gameState.unitById(declaration.attackerId)!!
    val target = gameState.unitById(declaration.targetId)!!
    val weapon = attacker.weapons[declaration.weaponIndex]

    val distance = attacker.position.distanceTo(target.position)
    val (rangeModifier, rangeBand) = when {
        distance <= weapon.shortRange -> 0 to RangeBand.SHORT
        distance <= weapon.mediumRange -> 2 to RangeBand.MEDIUM
        distance <= weapon.longRange -> 4 to RangeBand.LONG
        else -> 99 to RangeBand.OUT_OF_RANGE
    }

    val heatPenalty = heatPenaltyModifier(attacker)
    val secondaryPenalty = if (declaration.isPrimary) 0 else 1
    val proneModifier = proneTargetToHitModifier(target, distance)
    val immobileModifier = immobileTargetToHitModifier(target)
    val targetNumber = attacker.gunnerySkill + rangeModifier + heatPenalty +
        secondaryPenalty + proneModifier + immobileModifier

    val toHitRoll = roller.roll2d6()

    return if (toHitRoll.total >= targetNumber) {
        val locationRoll = roller.roll2d6()
        val hitLocation = HitLocationTable.roll(locationRoll.total)
        AttackResult(
            attackerId = declaration.attackerId,
            targetId = declaration.targetId,
            weaponName = weapon.name,
            hit = true,
            hitLocation = hitLocation,
            damageApplied = weapon.damage,
            targetNumber = targetNumber,
            roll = toHitRoll.total,
            toHitRoll = toHitRoll,
            locationRoll = locationRoll,
            gunnery = attacker.gunnerySkill,
            rangeModifier = rangeModifier,
            rangeBand = rangeBand,
            heatPenalty = heatPenalty,
            secondaryPenalty = secondaryPenalty,
        )
    } else {
        AttackResult(
            attackerId = declaration.attackerId,
            targetId = declaration.targetId,
            weaponName = weapon.name,
            hit = false,
            hitLocation = null,
            damageApplied = 0,
            targetNumber = targetNumber,
            roll = toHitRoll.total,
            toHitRoll = toHitRoll,
            locationRoll = null,
            gunnery = attacker.gunnerySkill,
            rangeModifier = rangeModifier,
            rangeBand = rangeBand,
            heatPenalty = heatPenalty,
            secondaryPenalty = secondaryPenalty,
        )
    }
}

public fun heatPenaltyModifier(actor: CombatUnit): Int =
    HeatScale.toHitPenalty(actor.currentHeat)

private fun getArmor(armor: ArmorLayout, location: HitLocation, rear: Boolean): Int = when {
    rear && location == HitLocation.CENTER_TORSO -> armor.centerTorsoRear
    rear && location == HitLocation.LEFT_TORSO -> armor.leftTorsoRear
    rear && location == HitLocation.RIGHT_TORSO -> armor.rightTorsoRear
    else -> when (location) {
        HitLocation.HEAD -> armor.head
        HitLocation.CENTER_TORSO -> armor.centerTorso
        HitLocation.LEFT_TORSO -> armor.leftTorso
        HitLocation.RIGHT_TORSO -> armor.rightTorso
        HitLocation.LEFT_ARM -> armor.leftArm
        HitLocation.RIGHT_ARM -> armor.rightArm
        HitLocation.LEFT_LEG -> armor.leftLeg
        HitLocation.RIGHT_LEG -> armor.rightLeg
    }
}

private fun setArmor(armor: ArmorLayout, location: HitLocation, value: Int, rear: Boolean): ArmorLayout = when {
    rear && location == HitLocation.CENTER_TORSO -> armor.copy(centerTorsoRear = value)
    rear && location == HitLocation.LEFT_TORSO -> armor.copy(leftTorsoRear = value)
    rear && location == HitLocation.RIGHT_TORSO -> armor.copy(rightTorsoRear = value)
    else -> when (location) {
        HitLocation.HEAD -> armor.copy(head = value)
        HitLocation.CENTER_TORSO -> armor.copy(centerTorso = value)
        HitLocation.LEFT_TORSO -> armor.copy(leftTorso = value)
        HitLocation.RIGHT_TORSO -> armor.copy(rightTorso = value)
        HitLocation.LEFT_ARM -> armor.copy(leftArm = value)
        HitLocation.RIGHT_ARM -> armor.copy(rightArm = value)
        HitLocation.LEFT_LEG -> armor.copy(leftLeg = value)
        HitLocation.RIGHT_LEG -> armor.copy(rightLeg = value)
    }
}

private fun getInternalStructure(is_: InternalStructureLayout, location: HitLocation): Int = when (location) {
    HitLocation.HEAD -> is_.head
    HitLocation.CENTER_TORSO -> is_.centerTorso
    HitLocation.LEFT_TORSO -> is_.leftTorso
    HitLocation.RIGHT_TORSO -> is_.rightTorso
    HitLocation.LEFT_ARM -> is_.leftArm
    HitLocation.RIGHT_ARM -> is_.rightArm
    HitLocation.LEFT_LEG -> is_.leftLeg
    HitLocation.RIGHT_LEG -> is_.rightLeg
}

private fun setInternalStructure(is_: InternalStructureLayout, location: HitLocation, value: Int): InternalStructureLayout = when (location) {
    HitLocation.HEAD -> is_.copy(head = value)
    HitLocation.CENTER_TORSO -> is_.copy(centerTorso = value)
    HitLocation.LEFT_TORSO -> is_.copy(leftTorso = value)
    HitLocation.RIGHT_TORSO -> is_.copy(rightTorso = value)
    HitLocation.LEFT_ARM -> is_.copy(leftArm = value)
    HitLocation.RIGHT_ARM -> is_.copy(rightArm = value)
    HitLocation.LEFT_LEG -> is_.copy(leftLeg = value)
    HitLocation.RIGHT_LEG -> is_.copy(rightLeg = value)
}
