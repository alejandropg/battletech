package battletech.tactical.unit

public object Weapons {
    public fun mediumLaser(): Weapon = Weapon(
        name = "Medium Laser",
        damage = 5,
        heat = 3,
        shortRange = 3,
        mediumRange = 6,
        longRange = 9,
        criticalSlots = 1,
    )

    public fun largeLaser(): Weapon = Weapon(
        name = "Large Laser",
        damage = 8,
        heat = 8,
        shortRange = 5,
        mediumRange = 10,
        longRange = 15,
        criticalSlots = 2,
    )

    public fun ac20(): Weapon = Weapon(
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

    public fun srm6(): Weapon = Weapon(
        name = "SRM 6",
        damage = 12,
        heat = 4,
        shortRange = 3,
        mediumRange = 6,
        longRange = 9,
        criticalSlots = 2,
        ammoType = AmmoType.SRM6,
    )

    public fun smallLaser(): Weapon = Weapon(
        name = "Small Laser",
        damage = 3,
        heat = 1,
        shortRange = 1,
        mediumRange = 2,
        longRange = 3,
        criticalSlots = 1,
    )

    public fun machineGun(): Weapon = Weapon(
        name = "Machine Gun",
        damage = 2,
        heat = 0,
        shortRange = 1,
        mediumRange = 2,
        longRange = 3,
        criticalSlots = 1,
        ammoType = AmmoType.MG,
    )

    public fun srm2(): Weapon = Weapon(
        name = "SRM 2",
        damage = 4,
        heat = 2,
        shortRange = 3,
        mediumRange = 6,
        longRange = 9,
        criticalSlots = 1,
        ammoType = AmmoType.SRM2,
    )

    public fun ppc(): Weapon = Weapon(
        name = "PPC",
        damage = 10,
        heat = 10,
        minimumRange = 3,
        shortRange = 6,
        mediumRange = 12,
        longRange = 18,
        criticalSlots = 3,
    )

    public fun lrm5(): Weapon = Weapon(
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

    public fun lrm10(): Weapon = Weapon(
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

    public fun lrm20(): Weapon = Weapon(
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

    public fun ac5(): Weapon = Weapon(
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
