package battletech.tactical.attack.physical

import battletech.tactical.attack.HitLocation

/** Kick Location Table (`docs/rules/physical-attacks.md` §7). */
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
