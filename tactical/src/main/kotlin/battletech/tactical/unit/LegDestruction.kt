package battletech.tactical.unit

import battletech.tactical.model.MechLocation

/**
 * Number of legs whose internal structure has reached 0 on this unit (0, 1, or 2).
 *
 * A leg with IS = 0 is structurally destroyed: the unit cannot run or jump, walk speed
 * is halved, and a single such leg forces an immediate fall. Both legs destroyed triggers
 * [DestructionReason.BOTH_LEGS_DESTROYED] via [destructionReason].
 */
public fun CombatUnit.destroyedLegCount(): Int {
    var count = 0
    if (!internalStructure.isIntact(MechLocation.LEFT_LEG)) count++
    if (!internalStructure.isIntact(MechLocation.RIGHT_LEG)) count++
    return count
}

/**
 * PSR modifier applied to all piloting skill rolls while this unit has at least one
 * destroyed leg (+[LEG_PSR_PENALTY] per destroyed leg). Consumed by the movement phase
 * (stand-up attempts) and by [battletech.tactical.attack.applyLocationDestructionConsequences]
 * (fall PSR if future tasks add a PSR-or-fall on leg destruction; currently the fall is
 * automatic, so this modifier surfaces for stand-up and Task 6 forced-PSRs).
 */
public const val LEG_PSR_PENALTY: Int = 1

/**
 * PSR modifier applied to all piloting skill rolls while [unit] has at least one
 * destroyed leg (+[LEG_PSR_PENALTY] per destroyed leg), mirroring the [gyroPsrModifier]
 * pattern. Consumed by [battletech.tactical.movement.MovementPhaseHandler] (stand-up
 * attempts) and available for Task 6 forced-PSR wiring.
 */
public fun legPsrModifier(unit: CombatUnit): Int = unit.destroyedLegCount() * LEG_PSR_PENALTY
