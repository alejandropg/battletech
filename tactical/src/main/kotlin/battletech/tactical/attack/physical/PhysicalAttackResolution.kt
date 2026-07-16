package battletech.tactical.attack.physical

import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.applyPilotHit
import battletech.tactical.attack.fall
import battletech.tactical.attack.resolveDamage
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.withUnit
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.basePsrModifier
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
        if (result is PhysicalAttackResult.Hit) {
            val target = updatedState.unitById(result.targetId)
            val resolution = resolveDamage(
                unit = target,
                location = result.hitLocation,
                damage = result.damageApplied,
                useRearArmor = result.attackDirection == AttackDirection.REAR,
            )
            updatedState = updatedState.withUnit(resolution.unit)
            declaration to result.withDamage(resolution.steps)
        } else {
            declaration to result
        }
    }

    // Pass 2: kicks force a knockdown PSR — the target on a hit, the attacker on a miss.
    val finalResults = damagedResults.map { (declaration, result) ->
        if (declaration.kind !is PhysicalAttackKind.Kick) return@map result

        val fallerId = if (result is PhysicalAttackResult.Hit) result.targetId else result.attackerId
        val faller = updatedState.unitById(fallerId)
        if (faller.isProne) return@map result

        // Include gyro + leg PSR penalties in the knockdown roll.
        val psrModifier = faller.basePsrModifier()
        val psr = pilotingSkillRoll(faller, roller, modifier = psrModifier)
        val knockdown = if (psr.passed) {
            Knockdown.Resisted.Detailed(psr)
        } else {
            // Fall applies damage; pilot also takes 1 hit per standard BT rules.
            // Canonical dice order: fall location 2d6, facing 1d6, consciousness check 2d6.
            val (fallen, fallResult) = fall(faller, roller)
            val (injured, pilotEvents) = applyPilotHit(fallen, roller)
            updatedState = updatedState.withUnit(injured)
            Knockdown.Fell.Detailed(unitId = fallerId, psr = psr, fall = fallResult, pilotEvents = pilotEvents)
        }
        withKnockdown(result, knockdown)
    }

    return updatedState to finalResults
}

private fun withKnockdown(result: PhysicalAttackResult, knockdown: Knockdown): PhysicalAttackResult = when (result) {
    is PhysicalAttackResult.Miss -> result.copy(knockdown = knockdown)
    is PhysicalAttackResult.Hit -> result.copy(knockdown = knockdown)
}

private fun resolveOnePhysicalAttack(
    declaration: PhysicalAttackDeclaration,
    gameState: GameState,
    roller: DiceRoller,
): PhysicalAttackResult {
    val attacker = gameState.unitById(declaration.attackerId)
    val target = gameState.unitById(declaration.targetId)
    val direction = attackDirection(attacker, target)

    // target is passed as-is: see the equivalent note in AttackResolution.resolveOneAttack —
    // the shared math only reads target's public projection, and CombatUnit is a VisibleUnit.
    val targetNumber = physicalToHitTargetNumber(attacker, target, declaration.kind, gameState.map)
    val toHitRoll = roller.roll2d6()

    if (toHitRoll.total < targetNumber) {
        return PhysicalAttackResult.Miss(
            attackerId = declaration.attackerId,
            targetId = declaration.targetId,
            attackName = attackName(declaration),
            targetNumber = targetNumber,
            toHitRoll = toHitRoll,
            attackDirection = direction,
        )
    }

    val locationRoll = roller.d6()
    val hitLocation = locationFor(declaration, locationRoll, direction)
    return PhysicalAttackResult.Hit(
        attackerId = declaration.attackerId,
        targetId = declaration.targetId,
        attackName = attackName(declaration),
        targetNumber = targetNumber,
        toHitRoll = toHitRoll,
        attackDirection = direction,
        hitLocation = hitLocation,
        locationRoll = locationRoll,
        damageApplied = damageFor(declaration, attacker),
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
