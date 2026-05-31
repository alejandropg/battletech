package battletech.tactical.attack.physical

import battletech.tactical.model.GameState
import battletech.tactical.session.RuleRejection

/**
 * Validates the per-turn physical-attack limits for a committed impulse,
 * returning the first [RuleRejection] found or `null` if the declarations are
 * legal. Total Warfare limits, enforced per attacking unit:
 *  - a unit may punch with each arm (up to two punches) XOR kick once;
 *  - a unit may not reuse the same limb;
 *  - a unit may not use a destroyed (0 internal structure) limb.
 */
public fun physicalImpulseViolation(
    declarations: List<PhysicalAttackDeclaration>,
    gameState: GameState,
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

        val attacker = gameState.unitById(attackerId)
        val usedLimbs = mutableSetOf<Pair<Boolean, Side>>()
        for (decl in decls) {
            val (isPunch, side) = when (val kind = decl.kind) {
                is PhysicalAttackKind.Punch -> true to kind.arm
                is PhysicalAttackKind.Kick -> false to kind.leg
            }
            if (!usedLimbs.add(isPunch to side)) {
                return RuleRejection.LimbAlreadyUsed(attackerId)
            }
            if (attacker != null && limbInternalStructure(attacker, isPunch, side) <= 0) {
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
