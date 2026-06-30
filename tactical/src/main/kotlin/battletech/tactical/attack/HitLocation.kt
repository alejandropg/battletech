package battletech.tactical.attack

import battletech.tactical.attack.physical.AttackDirection
import battletech.tactical.model.MechLocation

public typealias HitLocation = MechLocation

public object HitLocationTable {

    /**
     * Returns the hit location for a 2d6 [dieResult] from the canonical BattleTech
     * Mech Hit Location Table for the given [direction].
     *
     * Front and Rear attacks share the same column; Left and Right have their own columns.
     * The [direction] parameter defaults to [AttackDirection.FRONT] so existing callers
     * (e.g. fall damage) continue to compile and behave unchanged.
     */
    public fun roll(dieResult: Int, direction: AttackDirection = AttackDirection.FRONT): HitLocation {
        if (dieResult !in 2..12) error("Invalid 2d6 result: $dieResult (must be 2-12)")
        val column = when (direction) {
            AttackDirection.FRONT, AttackDirection.REAR -> FRONT_REAR
            AttackDirection.LEFT -> LEFT
            AttackDirection.RIGHT -> RIGHT
        }
        return column[dieResult - 2]
    }

    // Total Warfare Mech Hit Location Table — Front/Rear column (identical for both arcs).
    private val FRONT_REAR = listOf(
        /* 2  */ HitLocation.CENTER_TORSO,
        /* 3  */ HitLocation.RIGHT_ARM,
        /* 4  */ HitLocation.RIGHT_ARM,
        /* 5  */ HitLocation.RIGHT_LEG,
        /* 6  */ HitLocation.RIGHT_TORSO,
        /* 7  */ HitLocation.CENTER_TORSO,
        /* 8  */ HitLocation.LEFT_TORSO,
        /* 9  */ HitLocation.LEFT_LEG,
        /* 10 */ HitLocation.LEFT_ARM,
        /* 11 */ HitLocation.LEFT_ARM,
        /* 12 */ HitLocation.HEAD,
    )

    // Left-side column: attacker is on the target's left.
    private val LEFT = listOf(
        /* 2  */ HitLocation.LEFT_TORSO,
        /* 3  */ HitLocation.LEFT_LEG,
        /* 4  */ HitLocation.LEFT_ARM,
        /* 5  */ HitLocation.LEFT_ARM,
        /* 6  */ HitLocation.LEFT_LEG,
        /* 7  */ HitLocation.LEFT_TORSO,
        /* 8  */ HitLocation.CENTER_TORSO,
        /* 9  */ HitLocation.RIGHT_TORSO,
        /* 10 */ HitLocation.RIGHT_ARM,
        /* 11 */ HitLocation.RIGHT_LEG,
        /* 12 */ HitLocation.HEAD,
    )

    // Right-side column: attacker is on the target's right.
    private val RIGHT = listOf(
        /* 2  */ HitLocation.RIGHT_TORSO,
        /* 3  */ HitLocation.RIGHT_LEG,
        /* 4  */ HitLocation.RIGHT_ARM,
        /* 5  */ HitLocation.RIGHT_ARM,
        /* 6  */ HitLocation.RIGHT_LEG,
        /* 7  */ HitLocation.RIGHT_TORSO,
        /* 8  */ HitLocation.CENTER_TORSO,
        /* 9  */ HitLocation.LEFT_TORSO,
        /* 10 */ HitLocation.LEFT_ARM,
        /* 11 */ HitLocation.LEFT_LEG,
        /* 12 */ HitLocation.HEAD,
    )
}
