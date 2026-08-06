package battletech.tactical.attack.physical

import battletech.tactical.model.GameState
import battletech.tactical.session.RuleRejection
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId

/**
 * Validates the per-turn physical-attack limits (`docs/rules/physical-attacks.md` §1) for
 * a committed impulse, returning the first [RuleRejection] found or `null` if the
 * declarations are legal.
 */
public fun physicalImpulseViolation(
    declarations: List<PhysicalAttackDeclaration>,
    gameState: GameState,
): RuleRejection? = physicalImpulseViolation(declarations) { gameState.units.byId(it) }

/**
 * Lookup-based overload of [physicalImpulseViolation]: the rule only ever
 * resolves each declaration's own attacker, never any other unit on the
 * table, so a caller that can resolve just those attacker ids — e.g. a
 * delivery validating its own player's in-progress declarations without
 * holding the full [GameState] — can call this directly via [unitById].
 */
public fun physicalImpulseViolation(
    declarations: List<PhysicalAttackDeclaration>,
    unitById: (UnitId) -> CombatUnit,
): RuleRejection? {
    for ((attackerId, decls) in declarations.groupBy { it.attackerId }) {
        val hasPunch = decls.any { it.kind is PhysicalAttackKind.Punch }
        val hasKick = decls.any { it.kind is PhysicalAttackKind.Kick }
        if (hasPunch && hasKick) {
            return RuleRejection.PunchAndKickSameTurn(attackerId)
        }
        if (decls.count { it.kind is PhysicalAttackKind.Kick } > 1) {
            return RuleRejection.LimbAlreadyUsed(attackerId)
        }

        val attacker = unitById(attackerId)
        val usedLimbs = mutableSetOf<Pair<Boolean, Side>>()
        for (decl in decls) {
            val (isPunch, side) = when (val kind = decl.kind) {
                is PhysicalAttackKind.Punch -> true to kind.arm
                is PhysicalAttackKind.Kick -> false to kind.leg
            }
            if (!usedLimbs.add(isPunch to side)) {
                return RuleRejection.LimbAlreadyUsed(attackerId)
            }
            if (limbInternalStructure(attacker, isPunch, side) <= 0) {
                return RuleRejection.LimbDestroyed(attackerId)
            }
        }
    }
    return null
}

private fun limbInternalStructure(
    attacker: battletech.tactical.unit.CombatUnit,
    isPunch: Boolean,
    side: Side,
): Int {
    val structure = attacker.internalStructure
    return when {
        isPunch && side == Side.LEFT -> structure.leftArm
        isPunch && side == Side.RIGHT -> structure.rightArm
        !isPunch && side == Side.LEFT -> structure.leftLeg
        else -> structure.rightLeg
    }
}
