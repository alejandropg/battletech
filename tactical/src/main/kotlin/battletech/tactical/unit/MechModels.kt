package battletech.tactical.unit

import battletech.tactical.model.MechLocation.CENTER_TORSO
import battletech.tactical.model.MechLocation.LEFT_ARM
import battletech.tactical.model.MechLocation.LEFT_TORSO
import battletech.tactical.model.MechLocation.RIGHT_ARM
import battletech.tactical.model.MechLocation.RIGHT_TORSO

private fun mech(
    variant: String,
    name: String,
    tonnage: Int,
    walkingMP: Int,
    runningMP: Int,
    jumpMP: Int = 0,
    heatSink: HeatSink = HeatSink(HeatSinkType.STS, 10),
    armor: ArmorLayout,
    build: CriticalLayoutBuilder.() -> Unit,
): MechModel {
    val b = mechLayout(build)
    return MechModel(
        variant = variant,
        name = name,
        tonnage = tonnage,
        walkingMP = walkingMP,
        runningMP = runningMP,
        jumpMP = jumpMP,
        heatSink = heatSink,
        armor = armor,
        internalStructure = InternalStructureTables.forTonnage(tonnage),
        criticalLayout = b.layout,
        weapons = b.weapons,
    )
}

public object MechModels {
    private val registry: Map<String, MechModel> = listOf(
        mech(
            variant = "LCT-1V",
            name = "Locust LCT-1V",
            tonnage = 20,
            walkingMP = 8,
            runningMP = 12,
            armor = ArmorLayout(
                head = 8,
                centerTorso = 10, centerTorsoRear = 2,
                leftTorso = 8, leftTorsoRear = 2,
                rightTorso = 8, rightTorsoRear = 2,
                leftArm = 4, rightArm = 4,
                leftLeg = 4, rightLeg = 4,
            ),
        ) {
            place(CENTER_TORSO, WeaponModels.mediumLaser)
            place(LEFT_TORSO, WeaponModels.machineGun)
            place(RIGHT_TORSO, WeaponModels.machineGun)
            ammo(CENTER_TORSO, AmmoType.MG, 1)
        },
        mech(
            variant = "STG-3R",
            name = "Stinger STG-3R",
            tonnage = 20,
            walkingMP = 6,
            runningMP = 9,
            jumpMP = 6,
            armor = ArmorLayout(
                head = 6,
                centerTorso = 6, centerTorsoRear = 2,
                leftTorso = 6, leftTorsoRear = 2,
                rightTorso = 6, rightTorsoRear = 2,
                leftArm = 4, rightArm = 4,
                leftLeg = 5, rightLeg = 5,
            ),
        ) {
            place(LEFT_ARM, WeaponModels.mediumLaser)
            place(RIGHT_ARM, WeaponModels.machineGun)
            place(RIGHT_ARM, WeaponModels.machineGun)
            ammo(CENTER_TORSO, AmmoType.MG, 1)
            jumpJets(6)
        },
        mech(
            variant = "WSP-1A",
            name = "Wasp WSP-1A",
            tonnage = 20,
            walkingMP = 6,
            runningMP = 9,
            jumpMP = 6,
            armor = ArmorLayout(
                head = 4,
                centerTorso = 6, centerTorsoRear = 2,
                leftTorso = 4, leftTorsoRear = 2,
                rightTorso = 4, rightTorsoRear = 2,
                leftArm = 4, rightArm = 4,
                leftLeg = 5, rightLeg = 5,
            ),
        ) {
            place(RIGHT_ARM, WeaponModels.mediumLaser)
            place(LEFT_ARM, WeaponModels.srm2)
            ammo(LEFT_ARM, AmmoType.SRM2, 1)
            jumpJets(6)
        },
        mech(
            variant = "PXH-1",
            name = "Phoenix Hawk PXH-1",
            tonnage = 45,
            walkingMP = 6,
            runningMP = 9,
            jumpMP = 6,
            heatSink = HeatSink(HeatSinkType.STS, 13),
            armor = ArmorLayout(
                head = 6,
                centerTorso = 23, centerTorsoRear = 5,
                leftTorso = 18, leftTorsoRear = 4,
                rightTorso = 18, rightTorsoRear = 4,
                leftArm = 10, rightArm = 10,
                leftLeg = 15, rightLeg = 15,
            ),
        ) {
            place(RIGHT_ARM, WeaponModels.largeLaser)
            place(LEFT_ARM, WeaponModels.mediumLaser)
            place(LEFT_ARM, WeaponModels.mediumLaser)
            place(RIGHT_TORSO, WeaponModels.machineGun)
            place(LEFT_TORSO, WeaponModels.machineGun)
            ammo(CENTER_TORSO, AmmoType.MG, 1)
            heatSinks(3)
            jumpJets(6)
        },
        mech(
            variant = "GRF-1N",
            name = "Griffin GRF-1N",
            tonnage = 55,
            walkingMP = 5,
            runningMP = 8,
            jumpMP = 5,
            heatSink = HeatSink(HeatSinkType.STS, 12),
            armor = ArmorLayout(
                head = 9,
                centerTorso = 20, centerTorsoRear = 7,
                leftTorso = 20, leftTorsoRear = 6,
                rightTorso = 20, rightTorsoRear = 6,
                leftArm = 14, rightArm = 14,
                leftLeg = 18, rightLeg = 18,
            ),
        ) {
            place(RIGHT_ARM, WeaponModels.ppc)
            place(RIGHT_TORSO, WeaponModels.lrm10)
            ammo(RIGHT_TORSO, AmmoType.LRM10, 1)
            heatSinks(2)
            jumpJets(5)
        },
        mech(
            variant = "SHD-2H",
            name = "Shadow Hawk SHD-2H",
            tonnage = 55,
            walkingMP = 5,
            runningMP = 8,
            jumpMP = 5,
            heatSink = HeatSink(HeatSinkType.STS, 12),
            armor = ArmorLayout(
                head = 9,
                centerTorso = 23, centerTorsoRear = 8,
                leftTorso = 18, leftTorsoRear = 6,
                rightTorso = 18, rightTorsoRear = 6,
                leftArm = 16, rightArm = 16,
                leftLeg = 26, rightLeg = 26,
            ),
        ) {
            place(RIGHT_TORSO, WeaponModels.ac5)
            ammo(RIGHT_TORSO, AmmoType.AC5, 1)
            place(LEFT_TORSO, WeaponModels.lrm5)
            ammo(LEFT_TORSO, AmmoType.LRM5, 1)
            place(LEFT_TORSO, WeaponModels.srm2)
            ammo(LEFT_TORSO, AmmoType.SRM2, 1)
            place(CENTER_TORSO, WeaponModels.mediumLaser)
            heatSinks(2)
            jumpJets(5)
        },
        mech(
            variant = "WHM-6R",
            name = "Warhammer WHM-6R",
            tonnage = 70,
            walkingMP = 4,
            runningMP = 6,
            heatSink = HeatSink(HeatSinkType.STS, 18),
            armor = ArmorLayout(
                head = 9,
                centerTorso = 20, centerTorsoRear = 8,
                leftTorso = 14, leftTorsoRear = 9,
                rightTorso = 14, rightTorsoRear = 9,
                leftArm = 20, rightArm = 20,
                leftLeg = 15, rightLeg = 15,
            ),
        ) {
            place(RIGHT_ARM, WeaponModels.ppc)
            place(LEFT_ARM, WeaponModels.ppc)
            place(RIGHT_TORSO, WeaponModels.mediumLaser)
            place(LEFT_TORSO, WeaponModels.mediumLaser)
            place(RIGHT_ARM, WeaponModels.smallLaser)
            place(LEFT_ARM, WeaponModels.smallLaser)
            place(LEFT_TORSO, WeaponModels.machineGun)
            place(LEFT_TORSO, WeaponModels.machineGun)
            ammo(LEFT_TORSO, AmmoType.MG, 1)
            place(RIGHT_TORSO, WeaponModels.srm6)
            ammo(RIGHT_TORSO, AmmoType.SRM6, 1)
            heatSinks(8)
        },
        mech(
            variant = "MAD-3R",
            name = "Marauder MAD-3R",
            tonnage = 75,
            walkingMP = 4,
            runningMP = 6,
            heatSink = HeatSink(HeatSinkType.STS, 18),
            armor = ArmorLayout(
                head = 9,
                centerTorso = 35, centerTorsoRear = 10,
                leftTorso = 17, leftTorsoRear = 8,
                rightTorso = 17, rightTorsoRear = 8,
                leftArm = 22, rightArm = 22,
                leftLeg = 18, rightLeg = 18,
            ),
        ) {
            place(RIGHT_ARM, WeaponModels.ppc)
            place(LEFT_ARM, WeaponModels.ppc)
            place(RIGHT_ARM, WeaponModels.mediumLaser)
            place(LEFT_ARM, WeaponModels.mediumLaser)
            place(RIGHT_TORSO, WeaponModels.ac5)
            ammo(RIGHT_TORSO, AmmoType.AC5, 1)
            heatSinks(8)
        },
        mech(
            variant = "ARC-2R",
            name = "Archer ARC-2R",
            tonnage = 70,
            walkingMP = 4,
            runningMP = 6,
            heatSink = HeatSink(HeatSinkType.STS, 15),
            armor = ArmorLayout(
                head = 9,
                centerTorso = 33, centerTorsoRear = 10,
                leftTorso = 24, leftTorsoRear = 6,
                rightTorso = 24, rightTorsoRear = 6,
                leftArm = 22, rightArm = 22,
                leftLeg = 26, rightLeg = 26,
            ),
        ) {
            place(RIGHT_TORSO, WeaponModels.lrm20)
            place(LEFT_TORSO, WeaponModels.lrm20)
            ammo(RIGHT_TORSO, AmmoType.LRM20, 2)
            ammo(LEFT_TORSO, AmmoType.LRM20, 2)
            place(CENTER_TORSO, WeaponModels.mediumLaser)
            place(CENTER_TORSO, WeaponModels.mediumLaser)
            place(RIGHT_TORSO, WeaponModels.mediumLaser)
            place(LEFT_TORSO, WeaponModels.mediumLaser)
            heatSinks(5)
        },
        mech(
            variant = "AS7-D",
            name = "Atlas AS7-D",
            tonnage = 100,
            walkingMP = 3,
            runningMP = 5,
            heatSink = HeatSink(HeatSinkType.STS, 20),
            armor = ArmorLayout(
                head = 9,
                centerTorso = 47, centerTorsoRear = 14,
                leftTorso = 32, leftTorsoRear = 10,
                rightTorso = 32, rightTorsoRear = 10,
                leftArm = 34, rightArm = 34,
                leftLeg = 41, rightLeg = 41,
            ),
        ) {
            place(RIGHT_TORSO, WeaponModels.ac20)
            ammo(RIGHT_TORSO, AmmoType.AC20, 2)
            place(LEFT_TORSO, WeaponModels.lrm20)
            ammo(LEFT_TORSO, AmmoType.LRM20, 2)
            place(CENTER_TORSO, WeaponModels.srm6)
            ammo(LEFT_TORSO, AmmoType.SRM6, 1)
            place(LEFT_ARM, WeaponModels.mediumLaser)
            place(RIGHT_ARM, WeaponModels.mediumLaser)
            place(LEFT_ARM, WeaponModels.mediumLaser)
            place(RIGHT_ARM, WeaponModels.mediumLaser)
            heatSinks(10)
        },
        mech(
            variant = "HBK-4G",
            name = "Hunchback HBK-4G",
            tonnage = 50,
            walkingMP = 4,
            runningMP = 6,
            armor = ArmorLayout(
                head = 9,
                centerTorso = 24, centerTorsoRear = 8,
                leftTorso = 20, leftTorsoRear = 4,
                rightTorso = 20, rightTorsoRear = 4,
                leftArm = 10, rightArm = 10,
                leftLeg = 16, rightLeg = 16,
            ),
        ) {
            place(RIGHT_TORSO, WeaponModels.ac20)
            ammo(RIGHT_TORSO, AmmoType.AC20, 2)
        },
        mech(
            variant = "WVR-6R",
            name = "Wolverine WVR-6R",
            tonnage = 55,
            walkingMP = 5,
            runningMP = 8,
            jumpMP = 5,
            armor = ArmorLayout(
                head = 9,
                centerTorso = 22, centerTorsoRear = 8,
                leftTorso = 16, leftTorsoRear = 5,
                rightTorso = 16, rightTorsoRear = 5,
                leftArm = 14, rightArm = 14,
                leftLeg = 18, rightLeg = 18,
            ),
        ) {
            place(RIGHT_TORSO, WeaponModels.srm6)
            ammo(RIGHT_TORSO, AmmoType.SRM6, 1)
            place(RIGHT_ARM, WeaponModels.mediumLaser)
            jumpJets(5)
        },
    ).associateBy { it.variant }.also { registry ->
        for (model in registry.values) {
            model.criticalLayout.validate(model.weapons)
        }
    }

    public operator fun get(variant: String): MechModel =
        registry[variant] ?: error("Unknown mech variant: $variant")
}
