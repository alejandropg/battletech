package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.InternalStructureLayout
import battletech.tactical.unit.UnitId
import kotlin.math.ceil

public data class AttackDeclaration(
    val attackerId: UnitId,
    val targetId: UnitId,
    val weaponIndex: Int,
    val isPrimary: Boolean,
)

public data class AttackResult(
    val attackerId: UnitId,
    val targetId: UnitId,
    val weaponName: String,
    val hit: Boolean,
    val hitLocation: HitLocation?,
    val damageApplied: Int,
    val targetNumber: Int,
    val roll: Int,
)

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
    for (result in results) {
        if (result.hit && result.hitLocation != null) {
            val target = updatedState.unitById(result.targetId) ?: continue
            val updatedTarget = applyDamage(target, result.hitLocation, result.damageApplied)
            updatedState = updatedState.copy(
                units = updatedState.units.map { if (it.id == result.targetId) updatedTarget else it },
            )
        }
    }

    return updatedState to results
}

public fun applyDamage(unit: CombatUnit, location: HitLocation, damage: Int): CombatUnit {
    val currentArmor = getArmor(unit.armor, location)
    val armorAfter = (currentArmor - damage).coerceAtLeast(0)
    val overflow = (damage - currentArmor).coerceAtLeast(0)

    val newArmor = setArmor(unit.armor, location, armorAfter)
    val newIS = if (overflow > 0) {
        val currentIS = getInternalStructure(unit.internalStructure, location)
        val isAfter = (currentIS - overflow).coerceAtLeast(0)
        setInternalStructure(unit.internalStructure, location, isAfter)
    } else {
        unit.internalStructure
    }

    return unit.copy(armor = newArmor, internalStructure = newIS)
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
    val rangeModifier = when {
        distance <= weapon.shortRange -> 0
        distance <= weapon.mediumRange -> 2
        distance <= weapon.longRange -> 4
        else -> 99
    }

    val heatPenalty = heatPenaltyModifier(attacker)
    val secondaryPenalty = if (declaration.isPrimary) 0 else 1
    val targetNumber = attacker.gunnerySkill + rangeModifier + heatPenalty + secondaryPenalty

    val roll = roller.roll2d6().total

    return if (roll >= targetNumber) {
        val locationRoll = roller.roll2d6().total
        val hitLocation = HitLocationTable.roll(locationRoll)
        AttackResult(
            attackerId = declaration.attackerId,
            targetId = declaration.targetId,
            weaponName = weapon.name,
            hit = true,
            hitLocation = hitLocation,
            damageApplied = weapon.damage,
            targetNumber = targetNumber,
            roll = roll,
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
            roll = roll,
        )
    }
}

private fun heatPenaltyModifier(actor: CombatUnit): Int {
    val excessHeat = actor.currentHeat - actor.heatSinkCapacity
    return if (excessHeat <= 0) 0 else ceil(excessHeat / 3.0).toInt()
}

private fun getArmor(armor: ArmorLayout, location: HitLocation): Int = when (location) {
    HitLocation.HEAD -> armor.head
    HitLocation.CENTER_TORSO -> armor.centerTorso
    HitLocation.LEFT_TORSO -> armor.leftTorso
    HitLocation.RIGHT_TORSO -> armor.rightTorso
    HitLocation.LEFT_ARM -> armor.leftArm
    HitLocation.RIGHT_ARM -> armor.rightArm
    HitLocation.LEFT_LEG -> armor.leftLeg
    HitLocation.RIGHT_LEG -> armor.rightLeg
}

private fun setArmor(armor: ArmorLayout, location: HitLocation, value: Int): ArmorLayout = when (location) {
    HitLocation.HEAD -> armor.copy(head = value)
    HitLocation.CENTER_TORSO -> armor.copy(centerTorso = value)
    HitLocation.LEFT_TORSO -> armor.copy(leftTorso = value)
    HitLocation.RIGHT_TORSO -> armor.copy(rightTorso = value)
    HitLocation.LEFT_ARM -> armor.copy(leftArm = value)
    HitLocation.RIGHT_ARM -> armor.copy(rightArm = value)
    HitLocation.LEFT_LEG -> armor.copy(leftLeg = value)
    HitLocation.RIGHT_LEG -> armor.copy(rightLeg = value)
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
