package battletech.tactical.unit

public object WeaponModels {
    public val mediumLaser: WeaponModel = WeaponModel(
        name = "Medium Laser",
        damage = 5,
        heat = 3,
        shortRange = 3,
        mediumRange = 6,
        longRange = 9,
        criticalSlots = 1,
    )

    public val largeLaser: WeaponModel = WeaponModel(
        name = "Large Laser",
        damage = 8,
        heat = 8,
        shortRange = 5,
        mediumRange = 10,
        longRange = 15,
        criticalSlots = 2,
    )

    public val ac20: WeaponModel = WeaponModel(
        name = "AC/20",
        damage = 20,
        heat = 7,
        minimumRange = 3,
        shortRange = 3,
        mediumRange = 6,
        longRange = 9,
        criticalSlots = 10,
        ammoType = AmmoType.AC20,
    )

    public val srm6: WeaponModel = WeaponModel(
        name = "SRM 6",
        damage = 12,
        heat = 4,
        shortRange = 3,
        mediumRange = 6,
        longRange = 9,
        criticalSlots = 2,
        ammoType = AmmoType.SRM6,
    )

    public val smallLaser: WeaponModel = WeaponModel(
        name = "Small Laser",
        damage = 3,
        heat = 1,
        shortRange = 1,
        mediumRange = 2,
        longRange = 3,
        criticalSlots = 1,
    )

    public val machineGun: WeaponModel = WeaponModel(
        name = "Machine Gun",
        damage = 2,
        heat = 0,
        shortRange = 1,
        mediumRange = 2,
        longRange = 3,
        criticalSlots = 1,
        ammoType = AmmoType.MG,
    )

    public val srm2: WeaponModel = WeaponModel(
        name = "SRM 2",
        damage = 4,
        heat = 2,
        shortRange = 3,
        mediumRange = 6,
        longRange = 9,
        criticalSlots = 1,
        ammoType = AmmoType.SRM2,
    )

    public val ppc: WeaponModel = WeaponModel(
        name = "PPC",
        damage = 10,
        heat = 10,
        minimumRange = 3,
        shortRange = 6,
        mediumRange = 12,
        longRange = 18,
        criticalSlots = 3,
    )

    public val lrm5: WeaponModel = WeaponModel(
        name = "LRM 5",
        damage = 5,
        heat = 2,
        minimumRange = 6,
        shortRange = 7,
        mediumRange = 14,
        longRange = 21,
        criticalSlots = 1,
        ammoType = AmmoType.LRM5,
    )

    public val lrm10: WeaponModel = WeaponModel(
        name = "LRM 10",
        damage = 10,
        heat = 4,
        minimumRange = 6,
        shortRange = 7,
        mediumRange = 14,
        longRange = 21,
        criticalSlots = 2,
        ammoType = AmmoType.LRM10,
    )

    public val lrm20: WeaponModel = WeaponModel(
        name = "LRM 20",
        damage = 20,
        heat = 6,
        minimumRange = 6,
        shortRange = 7,
        mediumRange = 14,
        longRange = 21,
        criticalSlots = 5,
        ammoType = AmmoType.LRM20,
    )

    public val ac5: WeaponModel = WeaponModel(
        name = "AC/5",
        damage = 5,
        heat = 1,
        minimumRange = 3,
        shortRange = 6,
        mediumRange = 12,
        longRange = 18,
        criticalSlots = 4,
        ammoType = AmmoType.AC5,
    )
}
