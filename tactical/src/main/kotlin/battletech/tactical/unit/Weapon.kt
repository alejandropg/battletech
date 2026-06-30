package battletech.tactical.unit

import battletech.tactical.model.MechLocation

public data class Weapon(
    public val model: WeaponModel,
    public val mountId: WeaponMountId? = null,
    public val location: MechLocation? = null,
    public val destroyed: Boolean = false,
) {
    public val name: String get() = model.name
    public val damage: Int get() = model.damage
    public val heat: Int get() = model.heat
    public val minimumRange: Int get() = model.minimumRange
    public val shortRange: Int get() = model.shortRange
    public val mediumRange: Int get() = model.mediumRange
    public val longRange: Int get() = model.longRange
    public val criticalSlots: Int get() = model.criticalSlots
    public val ammoType: AmmoType? get() = model.ammoType
    public val clusterSize: Int? get() = model.clusterSize
    public val damagePerMissile: Int get() = model.damagePerMissile
    public val missilesPerGroup: Int get() = model.missilesPerGroup
    public val underwaterCapable: Boolean get() = model.underwaterCapable
}
