package battletech.tactical.attack.physical

import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.model.HexDirection
import battletech.tactical.unit.CombatUnit

/** Which side of the target a physical attack strikes — selects the hit-location column. */
public enum class AttackDirection { FRONT, RIGHT, REAR, LEFT }

/**
 * Attack direction from the target's [targetFacing] and the [bearingToAttacker]
 * (the hexside the attacker occupies relative to the target). Front is the
 * facing hexside, Rear the opposite; the two clockwise hexsides are Right and
 * the two counter-clockwise are Left.
 */
public fun attackDirectionFor(
    targetFacing: HexDirection,
    bearingToAttacker: HexDirection,
): AttackDirection {
    val relative = (bearingToAttacker.ordinal - targetFacing.ordinal + DIRECTION_COUNT) % DIRECTION_COUNT
    return when (relative) {
        0 -> AttackDirection.FRONT
        1, 2 -> AttackDirection.RIGHT
        3 -> AttackDirection.REAR
        else -> AttackDirection.LEFT
    }
}

/** Attack direction for an [attacker] striking a [target], from their board positions. */
public fun attackDirection(attacker: CombatUnit, target: CombatUnit): AttackDirection =
    attackDirectionFor(
        targetFacing = target.facing,
        bearingToAttacker = FiringArc.bearingDirection(target.position, attacker.position),
    )

private const val DIRECTION_COUNT = 6
