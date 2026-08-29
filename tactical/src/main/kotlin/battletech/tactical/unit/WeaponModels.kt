package battletech.tactical.unit

public object WeaponModels {
    public val mediumLaser: WeaponModel = WeaponModel(
        name = "Medium Laser",
        damage = 5,
        heat = 3,
        shortRange = 3,
        mediumRange = 6,
        longRange = 9,
        kind = WeaponKind.Energy,
    )

    public val largeLaser: WeaponModel = WeaponModel(
        name = "Large Laser",
        damage = 8,
        heat = 8,
        shortRange = 5,
        mediumRange = 10,
        longRange = 15,
        criticalSlots = 2,
        kind = WeaponKind.Energy,
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
        kind = WeaponKind.Ballistic(AmmoType.AC20),
    )

    public val srm6: WeaponModel = WeaponModel(
        name = "SRM 6",
        damage = 12,
        heat = 4,
        shortRange = 3,
        mediumRange = 6,
        longRange = 9,
        criticalSlots = 2,
        kind = WeaponKind.Missile(AmmoType.SRM6, clusterSize = 6, damagePerMissile = 2, missilesPerGroup = 1),
    )

    public val smallLaser: WeaponModel = WeaponModel(
        name = "Small Laser",
        damage = 3,
        heat = 1,
        shortRange = 1,
        mediumRange = 2,
        longRange = 3,
        kind = WeaponKind.Energy,
    )

    public val machineGun: WeaponModel = WeaponModel(
        name = "Machine Gun",
        damage = 2,
        heat = 0,
        shortRange = 1,
        mediumRange = 2,
        longRange = 3,
        kind = WeaponKind.Ballistic(AmmoType.MG),
    )

    public val srm2: WeaponModel = WeaponModel(
        name = "SRM 2",
        damage = 4,
        heat = 2,
        shortRange = 3,
        mediumRange = 6,
        longRange = 9,
        kind = WeaponKind.Missile(AmmoType.SRM2, clusterSize = 2, damagePerMissile = 2, missilesPerGroup = 1),
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
        kind = WeaponKind.Energy,
    )

    public val lrm5: WeaponModel = WeaponModel(
        name = "LRM 5",
        damage = 5,
        heat = 2,
        minimumRange = 6,
        shortRange = 7,
        mediumRange = 14,
        longRange = 21,
        kind = WeaponKind.Missile(AmmoType.LRM5, clusterSize = 5, damagePerMissile = 1, missilesPerGroup = 5),
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
        kind = WeaponKind.Missile(AmmoType.LRM10, clusterSize = 10, damagePerMissile = 1, missilesPerGroup = 5),
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
        kind = WeaponKind.Missile(AmmoType.LRM20, clusterSize = 20, damagePerMissile = 1, missilesPerGroup = 5),
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
        kind = WeaponKind.Ballistic(AmmoType.AC5),
    )

    private val registry: Map<String, WeaponModel> = mapOf(
        "mediumLaser" to mediumLaser,
        "largeLaser" to largeLaser,
        "ac20" to ac20,
        "srm6" to srm6,
        "smallLaser" to smallLaser,
        "machineGun" to machineGun,
        "srm2" to srm2,
        "ppc" to ppc,
        "lrm5" to lrm5,
        "lrm10" to lrm10,
        "lrm20" to lrm20,
        "ac5" to ac5,
    )

    /** Finds a weapon definition by its stable mech-file identifier. */
    public fun find(id: String): WeaponModel? = registry[id]

    /** Every stable identifier accepted by mech collection files. */
    public val ids: Set<String> get() = registry.keys
}
