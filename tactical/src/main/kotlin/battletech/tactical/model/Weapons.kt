package battletech.tactical.model

public object Weapons {
    public fun mediumLaser(): Weapon = Weapon(
        name = "Medium Laser",
        damage = 5,
        heat = 3,
        shortRange = 3,
        mediumRange = 6,
        longRange = 9,
    )

    public fun largeLaser(): Weapon = Weapon(
        name = "Large Laser",
        damage = 8,
        heat = 8,
        shortRange = 5,
        mediumRange = 10,
        longRange = 15,
    )

    public fun ac20(): Weapon = Weapon(
        name = "AC/20",
        damage = 20,
        heat = 7,
        minimumRange = 3,
        shortRange = 3,
        mediumRange = 6,
        longRange = 9,
        ammo = 5,
    )

    public fun srm6(): Weapon = Weapon(
        name = "SRM 6",
        damage = 12,
        heat = 4,
        shortRange = 3,
        mediumRange = 6,
        longRange = 9,
        ammo = 15,
    )
}
