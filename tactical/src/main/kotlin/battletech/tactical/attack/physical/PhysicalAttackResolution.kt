package battletech.tactical.attack.physical

import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.applyDamage
import battletech.tactical.attack.heatPenaltyModifier
import battletech.tactical.dice.DiceRoll
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId

/** Outcome of resolving one declared physical attack. */
public data class PhysicalAttackResult(
    public val attackerId: UnitId,
    public val targetId: UnitId,
    public val attackName: String,
    public val hit: Boolean,
    public val hitLocation: HitLocation?,
    public val damageApplied: Int,
    public val targetNumber: Int,
    public val roll: Int,
    public val toHitRoll: DiceRoll,
    public val locationRoll: Int?,
    public val attackDirection: AttackDirection,
)

/**
 * Resolves [declarations] simultaneously against [gameState]: every attack
 * rolls to-hit against the original state, then all damage is applied. Returns
 * the updated state and a [PhysicalAttackResult] per declaration.
 */
public fun resolvePhysicalAttacks(
    declarations: List<PhysicalAttackDeclaration>,
    gameState: GameState,
    roller: DiceRoller,
): Pair<GameState, List<PhysicalAttackResult>> {
    val results = declarations.map { resolveOnePhysicalAttack(it, gameState, roller) }

    var updatedState = gameState
    for (result in results) {
        if (result.hit && result.hitLocation != null) {
            val target = updatedState.unitById(result.targetId) ?: continue
            val updatedTarget = applyDamage(
                unit = target,
                location = result.hitLocation,
                damage = result.damageApplied,
                useRearArmor = result.attackDirection == AttackDirection.REAR,
            )
            updatedState = updatedState.copy(
                units = updatedState.units.map { if (it.id == result.targetId) updatedTarget else it },
            )
        }
    }

    return updatedState to results
}

private fun resolveOnePhysicalAttack(
    declaration: PhysicalAttackDeclaration,
    gameState: GameState,
    roller: DiceRoller,
): PhysicalAttackResult {
    val attacker = gameState.unitById(declaration.attackerId)!!
    val target = gameState.unitById(declaration.targetId)!!
    val direction = attackDirection(attacker, target)

    val targetNumber = attacker.pilotingSkill + heatPenaltyModifier(attacker) + attackModifier(declaration)
    val toHitRoll = roller.roll2d6()

    if (toHitRoll.total < targetNumber) {
        return PhysicalAttackResult(
            attackerId = declaration.attackerId,
            targetId = declaration.targetId,
            attackName = attackName(declaration),
            hit = false,
            hitLocation = null,
            damageApplied = 0,
            targetNumber = targetNumber,
            roll = toHitRoll.total,
            toHitRoll = toHitRoll,
            locationRoll = null,
            attackDirection = direction,
        )
    }

    val locationRoll = roller.d6()
    val hitLocation = locationFor(declaration, locationRoll, direction)
    return PhysicalAttackResult(
        attackerId = declaration.attackerId,
        targetId = declaration.targetId,
        attackName = attackName(declaration),
        hit = true,
        hitLocation = hitLocation,
        damageApplied = damageFor(declaration, attacker),
        targetNumber = targetNumber,
        roll = toHitRoll.total,
        toHitRoll = toHitRoll,
        locationRoll = locationRoll,
        attackDirection = direction,
    )
}

private fun attackModifier(declaration: PhysicalAttackDeclaration): Int = when (declaration.kind) {
    is PhysicalAttackKind.Punch -> 0
    is PhysicalAttackKind.Kick -> -2
}

private fun damageFor(declaration: PhysicalAttackDeclaration, attacker: CombatUnit): Int = when (declaration.kind) {
    is PhysicalAttackKind.Punch -> punchDamage(attacker)
    is PhysicalAttackKind.Kick -> kickDamage(attacker)
}

private fun locationFor(
    declaration: PhysicalAttackDeclaration,
    dieResult: Int,
    direction: AttackDirection,
): HitLocation = when (declaration.kind) {
    is PhysicalAttackKind.Punch -> PunchLocationTable.roll(dieResult, direction)
    is PhysicalAttackKind.Kick -> KickLocationTable.roll(dieResult, direction)
}

private fun attackName(declaration: PhysicalAttackDeclaration): String = when (declaration.kind) {
    is PhysicalAttackKind.Punch -> "Punch"
    is PhysicalAttackKind.Kick -> "Kick"
}
