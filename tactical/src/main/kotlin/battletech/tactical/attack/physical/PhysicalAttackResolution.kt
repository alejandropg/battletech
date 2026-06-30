package battletech.tactical.attack.physical

import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.applyPilotHit
import battletech.tactical.attack.fall
import battletech.tactical.attack.resolveDamage
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.withUnit
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.gyroPsrModifier
import battletech.tactical.unit.legPsrModifier
import battletech.tactical.unit.pilotingSkillRoll

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
    // Pass 1: roll every attack's to-hit and hit location against the original state.
    val resolved = declarations.map { it to resolveOnePhysicalAttack(it, gameState, roller) }

    // Apply all hit damage simultaneously.
    var updatedState = gameState
    val damagedResults = resolved.map { (declaration, result) ->
        if (result.hit && result.hitLocation != null) {
            val target = updatedState.unitById(result.targetId)
            val resolution = resolveDamage(
                unit = target,
                location = result.hitLocation,
                damage = result.damageApplied,
                useRearArmor = result.attackDirection == AttackDirection.REAR,
            )
            updatedState = updatedState.withUnit(resolution.unit)
            declaration to result.copy(damage = resolution.steps)
        } else {
            declaration to result
        }
    }

    // Pass 2: kicks force a knockdown PSR — the target on a hit, the attacker on a miss.
    val finalResults = damagedResults.map { (declaration, result) ->
        if (declaration.kind !is PhysicalAttackKind.Kick) return@map result

        val fallerId = if (result.hit) result.targetId else result.attackerId
        val faller = updatedState.unitById(fallerId)
        if (faller.isProne) return@map result

        // Include gyro + leg PSR penalties in the knockdown roll.
        val psrModifier = gyroPsrModifier(faller) + legPsrModifier(faller)
        val psr = pilotingSkillRoll(faller, roller, modifier = psrModifier)
        if (psr.passed) {
            result.copy(psr = psr)
        } else {
            // Fall applies damage; pilot also takes 1 hit per standard BT rules.
            // Canonical dice order: fall location 2d6, facing 1d6, consciousness check 2d6.
            val (fallen, fallResult) = fall(faller, roller)
            val (injured, pilotEvents) = applyPilotHit(fallen, roller)
            updatedState = updatedState.withUnit(injured)
            result.copy(psr = psr, fall = fallResult, fallenUnitId = fallerId, fallPilotEvents = pilotEvents)
        }
    }

    return updatedState to finalResults
}

private fun resolveOnePhysicalAttack(
    declaration: PhysicalAttackDeclaration,
    gameState: GameState,
    roller: DiceRoller,
): PhysicalAttackResult {
    val attacker = gameState.unitById(declaration.attackerId)
    val target = gameState.unitById(declaration.targetId)
    val direction = attackDirection(attacker, target)

    val targetNumber = physicalToHitTargetNumber(attacker, target, declaration.kind, gameState)
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
