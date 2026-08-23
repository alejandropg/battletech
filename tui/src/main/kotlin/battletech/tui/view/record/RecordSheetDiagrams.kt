package battletech.tui.view.record

import battletech.tactical.model.MechLocation
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.VisibleUnit

/**
 * Builds the [LocationDiagram]s shared by [MechRecordSheetView] and [ForeignRecordSheetView] —
 * one place that maps a unit's [battletech.tactical.unit.ArmorLayout]/
 * [battletech.tactical.unit.InternalStructureLayout] onto [LocationDiagram.Location] entries, so
 * the per-location field wiring exists exactly once.
 */
internal object RecordSheetDiagrams {

    /** The ARMOR DIAGRAM card. Works for any [VisibleUnit] — armor and its maximum are public. */
    internal fun armor(unit: VisibleUnit, destroyed: (MechLocation) -> Boolean = { false }): LocationDiagram {
        val armor = unit.armor
        val max = unit.maxArmor
        return LocationDiagram(
            title = "ARMOR DIAGRAM",
            silhouette = LocationDiagram.Silhouette.ARMOR,
            head = location("Head", armor.head, max.head, destroyed(MechLocation.HEAD)),
            leftArm = location("Left Arm", armor.leftArm, max.leftArm, destroyed(MechLocation.LEFT_ARM)),
            rightArm = location("Right Arm", armor.rightArm, max.rightArm, destroyed(MechLocation.RIGHT_ARM)),
            leftTorso = location("Left Torso", armor.leftTorso, max.leftTorso, destroyed(MechLocation.LEFT_TORSO)),
            rightTorso = location("Right Torso", armor.rightTorso, max.rightTorso, destroyed(MechLocation.RIGHT_TORSO)),
            centerTorso = location("Center Torso", armor.centerTorso, max.centerTorso, destroyed(MechLocation.CENTER_TORSO)),
            leftLeg = location("Left Leg", armor.leftLeg, max.leftLeg, destroyed(MechLocation.LEFT_LEG)),
            rightLeg = location("Right Leg", armor.rightLeg, max.rightLeg, destroyed(MechLocation.RIGHT_LEG)),
            leftTorsoRear = location(
                "Left Torso Rear",
                armor.leftTorsoRear,
                max.leftTorsoRear,
                destroyed(MechLocation.LEFT_TORSO),
            ),
            centerTorsoRear = location(
                "Center Torso Rear",
                armor.centerTorsoRear,
                max.centerTorsoRear,
                destroyed(MechLocation.CENTER_TORSO),
            ),
            rightTorsoRear = location(
                "Right Torso Rear",
                armor.rightTorsoRear,
                max.rightTorsoRear,
                destroyed(MechLocation.RIGHT_TORSO),
            ),
        )
    }

    /**
     * The INTERNAL STRUCTURE DIAGRAM card. Owner-only: [CombatUnit.internalStructure] and its
     * maximum are private record-sheet data, never exposed on the public [VisibleUnit] surface.
     */
    internal fun internalStructure(unit: CombatUnit): LocationDiagram {
        val current = unit.internalStructure
        val max = unit.maxInternalStructure
        val destroyed: (MechLocation) -> Boolean = { !current.isIntact(it) }
        return LocationDiagram(
            title = "INTERNAL STRUCTURE DIAGRAM",
            silhouette = LocationDiagram.Silhouette.INTERNAL_STRUCTURE,
            head = location("Head", current.head, max.head, destroyed(MechLocation.HEAD)),
            leftArm = location("Left Arm", current.leftArm, max.leftArm, destroyed(MechLocation.LEFT_ARM)),
            rightArm = location("Right Arm", current.rightArm, max.rightArm, destroyed(MechLocation.RIGHT_ARM)),
            leftTorso = location("Left Torso", current.leftTorso, max.leftTorso, destroyed(MechLocation.LEFT_TORSO)),
            rightTorso = location("Right Torso", current.rightTorso, max.rightTorso, destroyed(MechLocation.RIGHT_TORSO)),
            centerTorso = location("Center Torso", current.centerTorso, max.centerTorso, destroyed(MechLocation.CENTER_TORSO)),
            leftLeg = location("Left Leg", current.leftLeg, max.leftLeg, destroyed(MechLocation.LEFT_LEG)),
            rightLeg = location("Right Leg", current.rightLeg, max.rightLeg, destroyed(MechLocation.RIGHT_LEG)),
        )
    }

    private fun location(label: String, remaining: Int, max: Int, destroyed: Boolean) =
        LocationDiagram.Location(label, remaining, max, destroyed)
}
