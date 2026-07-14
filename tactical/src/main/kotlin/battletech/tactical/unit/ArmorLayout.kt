package battletech.tactical.unit

import battletech.tactical.model.MechLocation
import kotlinx.serialization.Serializable

@Serializable
public data class ArmorLayout(
    val head: Int,
    val centerTorso: Int,
    val centerTorsoRear: Int,
    val leftTorso: Int,
    val leftTorsoRear: Int,
    val rightTorso: Int,
    val rightTorsoRear: Int,
    val leftArm: Int,
    val rightArm: Int,
    val leftLeg: Int,
    val rightLeg: Int,
) {
    /**
     * Current armor at [location]. [rear] selects the rear-arc value for Center/Left/Right
     * Torso (the only locations with a rear facet); ignored for all other locations.
     */
    public fun at(location: MechLocation, rear: Boolean = false): Int = when {
        rear && location == MechLocation.CENTER_TORSO -> centerTorsoRear
        rear && location == MechLocation.LEFT_TORSO -> leftTorsoRear
        rear && location == MechLocation.RIGHT_TORSO -> rightTorsoRear
        else -> when (location) {
            MechLocation.HEAD -> head
            MechLocation.CENTER_TORSO -> centerTorso
            MechLocation.LEFT_TORSO -> leftTorso
            MechLocation.RIGHT_TORSO -> rightTorso
            MechLocation.LEFT_ARM -> leftArm
            MechLocation.RIGHT_ARM -> rightArm
            MechLocation.LEFT_LEG -> leftLeg
            MechLocation.RIGHT_LEG -> rightLeg
        }
    }

    /**
     * Returns a copy of this layout with [location]'s armor set to [value]. [rear] selects
     * the rear-arc facet for Center/Left/Right Torso; ignored for all other locations.
     */
    public fun with(location: MechLocation, value: Int, rear: Boolean = false): ArmorLayout = when {
        rear && location == MechLocation.CENTER_TORSO -> copy(centerTorsoRear = value)
        rear && location == MechLocation.LEFT_TORSO -> copy(leftTorsoRear = value)
        rear && location == MechLocation.RIGHT_TORSO -> copy(rightTorsoRear = value)
        else -> when (location) {
            MechLocation.HEAD -> copy(head = value)
            MechLocation.CENTER_TORSO -> copy(centerTorso = value)
            MechLocation.LEFT_TORSO -> copy(leftTorso = value)
            MechLocation.RIGHT_TORSO -> copy(rightTorso = value)
            MechLocation.LEFT_ARM -> copy(leftArm = value)
            MechLocation.RIGHT_ARM -> copy(rightArm = value)
            MechLocation.LEFT_LEG -> copy(leftLeg = value)
            MechLocation.RIGHT_LEG -> copy(rightLeg = value)
        }
    }
}
