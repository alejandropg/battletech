package battletech.tui.view

import battletech.tactical.model.MechLocation
import battletech.tactical.unit.ActuatorType
import battletech.tactical.unit.CriticalComponent
import battletech.tactical.unit.CriticalSlotContent
import battletech.tactical.unit.WeaponView

/**
 * Human-readable labels for the record-sheet vocabulary — [MechLocation]s, [ActuatorType]s,
 * [CriticalComponent]s, and [CriticalSlotContent] entries — shared by [GameLogFormatter] (event
 * lines) and the UNIT STATUS panel in both its NORMAL and maximized states. One place for this
 * wording keeps a player from seeing "Shoulder actuator" in the log and a differently-worded
 * synonym on the sheet for the same slot.
 */
internal object MechLabels {

    fun component(component: CriticalComponent): String = when (component) {
        CriticalComponent.ENGINE -> "Engine"
        CriticalComponent.GYRO -> "Gyro"
        CriticalComponent.SENSOR -> "Sensor"
        CriticalComponent.LIFE_SUPPORT -> "Life Support"
    }

    fun location(location: MechLocation): String = when (location) {
        MechLocation.HEAD -> "Head"
        MechLocation.CENTER_TORSO -> "Center Torso"
        MechLocation.LEFT_TORSO -> "Left Torso"
        MechLocation.RIGHT_TORSO -> "Right Torso"
        MechLocation.LEFT_ARM -> "Left Arm"
        MechLocation.RIGHT_ARM -> "Right Arm"
        MechLocation.LEFT_LEG -> "Left Leg"
        MechLocation.RIGHT_LEG -> "Right Leg"
    }

    /** The record sheet's two-letter location code — HD, CT, LT, RT, LA, RA, LL, RL. */
    fun abbreviation(location: MechLocation): String = when (location) {
        MechLocation.HEAD -> "HD"
        MechLocation.CENTER_TORSO -> "CT"
        MechLocation.LEFT_TORSO -> "LT"
        MechLocation.RIGHT_TORSO -> "RT"
        MechLocation.LEFT_ARM -> "LA"
        MechLocation.RIGHT_ARM -> "RA"
        MechLocation.LEFT_LEG -> "LL"
        MechLocation.RIGHT_LEG -> "RL"
    }

    fun actuator(type: ActuatorType): String = when (type) {
        ActuatorType.SHOULDER -> "Shoulder actuator"
        ActuatorType.UPPER_ARM -> "Upper arm actuator"
        ActuatorType.LOWER_ARM -> "Lower arm actuator"
        ActuatorType.HAND -> "Hand actuator"
        ActuatorType.HIP -> "Hip actuator"
        ActuatorType.UPPER_LEG -> "Upper leg actuator"
        ActuatorType.LOWER_LEG -> "Lower leg actuator"
        ActuatorType.FOOT -> "Foot actuator"
    }

    /**
     * [weapons] resolves a [CriticalSlotContent.WeaponMount] to the name of the weapon it
     * mounts — supplied lazily since it may require a unit lookup the caller only needs to
     * pay for when [content] actually is a [CriticalSlotContent.WeaponMount].
     */
    fun criticalSlotContent(content: CriticalSlotContent, weapons: () -> List<WeaponView>): String = when (content) {
        is CriticalSlotContent.Empty -> "empty slot"
        is CriticalSlotContent.Engine -> "Engine"
        is CriticalSlotContent.Gyro -> "Gyro"
        is CriticalSlotContent.Sensors -> "Sensors"
        is CriticalSlotContent.LifeSupport -> "Life Support"
        is CriticalSlotContent.Cockpit -> "Cockpit"
        is CriticalSlotContent.HeatSink -> "Heat Sink"
        is CriticalSlotContent.JumpJet -> "Jump Jet"
        is CriticalSlotContent.Actuator -> actuator(content.type)
        is CriticalSlotContent.WeaponMount ->
            weapons().find { it.mountId == content.weaponId }?.name ?: "weapon"
        is CriticalSlotContent.AmmoBin -> "${content.type} ammo"
    }
}
