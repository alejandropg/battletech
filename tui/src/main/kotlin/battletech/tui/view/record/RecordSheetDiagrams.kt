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
    fun armor(unit: VisibleUnit, destroyed: (MechLocation) -> Boolean = { false }): LocationDiagram {
        val armor = unit.armor
        val max = unit.maxArmor
        return LocationDiagram(
            title = "ARMOR DIAGRAM",
            head = location("HD", armor.head, max.head, destroyed(MechLocation.HEAD)),
            leftArm = location("LA", armor.leftArm, max.leftArm, destroyed(MechLocation.LEFT_ARM)),
            rightArm = location("RA", armor.rightArm, max.rightArm, destroyed(MechLocation.RIGHT_ARM)),
            leftTorso = location("LT", armor.leftTorso, max.leftTorso, destroyed(MechLocation.LEFT_TORSO)),
            rightTorso = location("RT", armor.rightTorso, max.rightTorso, destroyed(MechLocation.RIGHT_TORSO)),
            centerTorso = location("CT", armor.centerTorso, max.centerTorso, destroyed(MechLocation.CENTER_TORSO)),
            leftLeg = location("LL", armor.leftLeg, max.leftLeg, destroyed(MechLocation.LEFT_LEG)),
            rightLeg = location("RL", armor.rightLeg, max.rightLeg, destroyed(MechLocation.RIGHT_LEG)),
            leftTorsoRear = location("LT-R", armor.leftTorsoRear, max.leftTorsoRear, destroyed(MechLocation.LEFT_TORSO)),
            centerTorsoRear = location("CT-R", armor.centerTorsoRear, max.centerTorsoRear, destroyed(MechLocation.CENTER_TORSO)),
            rightTorsoRear = location("RT-R", armor.rightTorsoRear, max.rightTorsoRear, destroyed(MechLocation.RIGHT_TORSO)),
        )
    }

    /**
     * The INTERNAL STRUCTURE DIAGRAM card. Owner-only: [CombatUnit.internalStructure] and its
     * maximum are private record-sheet data, never exposed on the public [VisibleUnit] surface.
     */
    fun internalStructure(unit: CombatUnit): LocationDiagram {
        val current = unit.internalStructure
        val max = unit.maxInternalStructure
        fun destroyed(loc: MechLocation) = !current.isIntact(loc)
        return LocationDiagram(
            title = "INTERNAL STRUCTURE DIAGRAM",
            head = location("HD", current.head, max.head, destroyed(MechLocation.HEAD)),
            leftArm = location("LA", current.leftArm, max.leftArm, destroyed(MechLocation.LEFT_ARM)),
            rightArm = location("RA", current.rightArm, max.rightArm, destroyed(MechLocation.RIGHT_ARM)),
            leftTorso = location("LT", current.leftTorso, max.leftTorso, destroyed(MechLocation.LEFT_TORSO)),
            rightTorso = location("RT", current.rightTorso, max.rightTorso, destroyed(MechLocation.RIGHT_TORSO)),
            centerTorso = location("CT", current.centerTorso, max.centerTorso, destroyed(MechLocation.CENTER_TORSO)),
            leftLeg = location("LL", current.leftLeg, max.leftLeg, destroyed(MechLocation.LEFT_LEG)),
            rightLeg = location("RL", current.rightLeg, max.rightLeg, destroyed(MechLocation.RIGHT_LEG)),
        )
    }

    private fun location(label: String, remaining: Int, max: Int, destroyed: Boolean) =
        LocationDiagram.Location(label, remaining, max, destroyed)
}
