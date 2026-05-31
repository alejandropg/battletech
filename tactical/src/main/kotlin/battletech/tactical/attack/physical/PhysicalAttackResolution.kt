package battletech.tactical.attack.physical

import battletech.tactical.attack.FallResult
import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.applyDamage
import battletech.tactical.attack.fall
import battletech.tactical.dice.DiceRoll
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.PilotingSkillRoll
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.pilotingSkillRoll

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
    /** Knockdown PSR forced by a kick (target on a hit, attacker on a miss); null for punches. */
    public val psr: PilotingSkillRoll? = null,
    /** The fall that resulted from a failed [psr], if any. */
    public val fall: FallResult? = null,
    /** Which unit fell as a consequence of this attack, if any. */
    public val fallenUnitId: UnitId? = null,
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
    // Pass 1: roll every attack's to-hit and hit location against the original state.
    val resolved = declarations.map { it to resolveOnePhysicalAttack(it, gameState, roller) }

    // Apply all hit damage simultaneously.
    var updatedState = gameState
    for ((_, result) in resolved) {
        if (result.hit && result.hitLocation != null) {
            val target = updatedState.unitById(result.targetId) ?: continue
            val updatedTarget = applyDamage(
                unit = target,
                location = result.hitLocation,
                damage = result.damageApplied,
                useRearArmor = result.attackDirection == AttackDirection.REAR,
            )
            updatedState = updatedState.replacing(updatedTarget)
        }
    }

    // Pass 2: kicks force a knockdown PSR — the target on a hit, the attacker on a miss.
    val finalResults = resolved.map { (declaration, result) ->
        if (declaration.kind !is PhysicalAttackKind.Kick) return@map result

        val fallerId = if (result.hit) result.targetId else result.attackerId
        val faller = updatedState.unitById(fallerId)
        if (faller == null || faller.isProne) return@map result

        val psr = pilotingSkillRoll(faller, roller)
        if (psr.passed) {
            result.copy(psr = psr)
        } else {
            val (fallen, fallResult) = fall(faller, roller)
            updatedState = updatedState.replacing(fallen)
            result.copy(psr = psr, fall = fallResult, fallenUnitId = fallerId)
        }
    }

    return updatedState to finalResults
}

private fun GameState.replacing(unit: CombatUnit): GameState =
    copy(units = units.map { if (it.id == unit.id) unit else it })

private fun resolveOnePhysicalAttack(
    declaration: PhysicalAttackDeclaration,
    gameState: GameState,
    roller: DiceRoller,
): PhysicalAttackResult {
    val attacker = gameState.unitById(declaration.attackerId)!!
    val target = gameState.unitById(declaration.targetId)!!
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
