package battletech.tactical.attack.physical

import battletech.tactical.attack.HitLocation

/**
 * Total Warfare Kick Location Table. A side kick always strikes the near leg;
 * a front or rear kick hits the right leg on 1-3 and the left leg on 4-6.
 */
public object KickLocationTable {

    public fun roll(dieResult: Int, direction: AttackDirection): HitLocation {
        require(dieResult in 1..6) { "Invalid 1d6 result: $dieResult (must be 1-6)" }
        return when (direction) {
            AttackDirection.LEFT -> HitLocation.LEFT_LEG
            AttackDirection.RIGHT -> HitLocation.RIGHT_LEG
            AttackDirection.FRONT, AttackDirection.REAR ->
                if (dieResult <= 3) HitLocation.RIGHT_LEG else HitLocation.LEFT_LEG
        }
    }
}
