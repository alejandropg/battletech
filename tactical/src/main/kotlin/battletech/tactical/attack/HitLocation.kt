package battletech.tactical.attack

import battletech.tactical.model.MechLocation

public typealias HitLocation = MechLocation

public object HitLocationTable {

    public fun roll(dieResult: Int): HitLocation = when (dieResult) {
        2 -> HitLocation.CENTER_TORSO
        3, 4 -> HitLocation.RIGHT_ARM
        5 -> HitLocation.RIGHT_LEG
        6 -> HitLocation.RIGHT_TORSO
        7 -> HitLocation.CENTER_TORSO
        8 -> HitLocation.LEFT_TORSO
        9 -> HitLocation.LEFT_LEG
        10, 11 -> HitLocation.LEFT_ARM
        12 -> HitLocation.HEAD
        else -> error("Invalid 2d6 result: $dieResult (must be 2-12)")
    }
}
