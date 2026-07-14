package battletech.tactical.unit

import battletech.tactical.model.MechLocation
import kotlinx.serialization.Serializable

@Serializable
public data class InternalStructureLayout(
    val head: Int,
    val centerTorso: Int,
    val leftTorso: Int,
    val rightTorso: Int,
    val leftArm: Int,
    val rightArm: Int,
    val leftLeg: Int,
    val rightLeg: Int,
) {
    /** Current internal structure remaining at [location]. */
    public fun at(location: MechLocation): Int = when (location) {
        MechLocation.HEAD -> head
        MechLocation.CENTER_TORSO -> centerTorso
        MechLocation.LEFT_TORSO -> leftTorso
        MechLocation.RIGHT_TORSO -> rightTorso
        MechLocation.LEFT_ARM -> leftArm
        MechLocation.RIGHT_ARM -> rightArm
        MechLocation.LEFT_LEG -> leftLeg
        MechLocation.RIGHT_LEG -> rightLeg
    }

    /** Returns a copy of this layout with [location]'s internal structure set to [value]. */
    public fun with(location: MechLocation, value: Int): InternalStructureLayout = when (location) {
        MechLocation.HEAD -> copy(head = value)
        MechLocation.CENTER_TORSO -> copy(centerTorso = value)
        MechLocation.LEFT_TORSO -> copy(leftTorso = value)
        MechLocation.RIGHT_TORSO -> copy(rightTorso = value)
        MechLocation.LEFT_ARM -> copy(leftArm = value)
        MechLocation.RIGHT_ARM -> copy(rightArm = value)
        MechLocation.LEFT_LEG -> copy(leftLeg = value)
        MechLocation.RIGHT_LEG -> copy(rightLeg = value)
    }

    /** True when [location]'s internal structure is still positive (not structurally destroyed). */
    public fun isIntact(location: MechLocation): Boolean = at(location) > 0
}
