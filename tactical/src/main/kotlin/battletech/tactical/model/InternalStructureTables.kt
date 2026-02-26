package battletech.tactical.model

public object InternalStructureTables {

    public fun forTonnage(tonnage: Int): InternalStructureLayout {
        val entry = TABLE[tonnage] ?: error("No internal structure table for tonnage: $tonnage")
        return entry
    }

    // Standard internal structure values per BattleTech tonnage class
    // Format: head, centerTorso, leftTorso/rightTorso, leftArm/rightArm, leftLeg/rightLeg
    private val TABLE: Map<Int, InternalStructureLayout> = mapOf(
        20 to InternalStructureLayout(head = 3, centerTorso = 6, leftTorso = 5, rightTorso = 5, leftArm = 3, rightArm = 3, leftLeg = 4, rightLeg = 4),
        25 to InternalStructureLayout(head = 3, centerTorso = 8, leftTorso = 6, rightTorso = 6, leftArm = 4, rightArm = 4, leftLeg = 6, rightLeg = 6),
        30 to InternalStructureLayout(head = 3, centerTorso = 10, leftTorso = 7, rightTorso = 7, leftArm = 5, rightArm = 5, leftLeg = 7, rightLeg = 7),
        35 to InternalStructureLayout(head = 3, centerTorso = 11, leftTorso = 8, rightTorso = 8, leftArm = 6, rightArm = 6, leftLeg = 8, rightLeg = 8),
        40 to InternalStructureLayout(head = 3, centerTorso = 12, leftTorso = 10, rightTorso = 10, leftArm = 6, rightArm = 6, leftLeg = 10, rightLeg = 10),
        45 to InternalStructureLayout(head = 3, centerTorso = 14, leftTorso = 11, rightTorso = 11, leftArm = 7, rightArm = 7, leftLeg = 11, rightLeg = 11),
        50 to InternalStructureLayout(head = 3, centerTorso = 16, leftTorso = 12, rightTorso = 12, leftArm = 8, rightArm = 8, leftLeg = 12, rightLeg = 12),
        55 to InternalStructureLayout(head = 3, centerTorso = 18, leftTorso = 13, rightTorso = 13, leftArm = 9, rightArm = 9, leftLeg = 13, rightLeg = 13),
        60 to InternalStructureLayout(head = 3, centerTorso = 20, leftTorso = 14, rightTorso = 14, leftArm = 10, rightArm = 10, leftLeg = 14, rightLeg = 14),
        65 to InternalStructureLayout(head = 3, centerTorso = 21, leftTorso = 15, rightTorso = 15, leftArm = 10, rightArm = 10, leftLeg = 15, rightLeg = 15),
        70 to InternalStructureLayout(head = 3, centerTorso = 22, leftTorso = 15, rightTorso = 15, leftArm = 11, rightArm = 11, leftLeg = 15, rightLeg = 15),
        75 to InternalStructureLayout(head = 3, centerTorso = 23, leftTorso = 16, rightTorso = 16, leftArm = 12, rightArm = 12, leftLeg = 16, rightLeg = 16),
        80 to InternalStructureLayout(head = 3, centerTorso = 25, leftTorso = 17, rightTorso = 17, leftArm = 13, rightArm = 13, leftLeg = 17, rightLeg = 17),
        85 to InternalStructureLayout(head = 3, centerTorso = 27, leftTorso = 18, rightTorso = 18, leftArm = 14, rightArm = 14, leftLeg = 18, rightLeg = 18),
        90 to InternalStructureLayout(head = 3, centerTorso = 29, leftTorso = 19, rightTorso = 19, leftArm = 15, rightArm = 15, leftLeg = 19, rightLeg = 19),
        95 to InternalStructureLayout(head = 3, centerTorso = 30, leftTorso = 20, rightTorso = 20, leftArm = 16, rightArm = 16, leftLeg = 20, rightLeg = 20),
        100 to InternalStructureLayout(head = 3, centerTorso = 31, leftTorso = 21, rightTorso = 21, leftArm = 17, rightArm = 17, leftLeg = 21, rightLeg = 21),
    )
}
