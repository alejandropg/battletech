package battletech.tactical.attack.physical

import battletech.tactical.attack.HitLocation

/**
 * Total Warfare Punch Location Table. Indexed by a 1d6 roll and the
 * [AttackDirection] (the target's struck side). Rear uses the same column as
 * Front.
 */
public object PunchLocationTable {

    public fun roll(dieResult: Int, direction: AttackDirection): HitLocation {
        require(dieResult in 1..6) { "Invalid 1d6 result: $dieResult (must be 1-6)" }
        val column = when (direction) {
            AttackDirection.FRONT, AttackDirection.REAR -> FRONT
            AttackDirection.LEFT -> LEFT
            AttackDirection.RIGHT -> RIGHT
        }
        return column[dieResult - 1]
    }

    private val FRONT = listOf(
        HitLocation.LEFT_ARM,
        HitLocation.LEFT_TORSO,
        HitLocation.CENTER_TORSO,
        HitLocation.RIGHT_TORSO,
        HitLocation.RIGHT_ARM,
        HitLocation.HEAD,
    )

    private val LEFT = listOf(
        HitLocation.LEFT_TORSO,
        HitLocation.LEFT_ARM,
        HitLocation.LEFT_TORSO,
        HitLocation.CENTER_TORSO,
        HitLocation.LEFT_ARM,
        HitLocation.HEAD,
    )

    private val RIGHT = listOf(
        HitLocation.RIGHT_TORSO,
        HitLocation.RIGHT_ARM,
        HitLocation.RIGHT_TORSO,
        HitLocation.CENTER_TORSO,
        HitLocation.RIGHT_ARM,
        HitLocation.HEAD,
    )
}
