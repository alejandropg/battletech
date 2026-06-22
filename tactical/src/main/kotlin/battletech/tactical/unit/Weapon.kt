package battletech.tactical.unit

import battletech.tactical.model.MechLocation

public data class Weapon(
    public val name: String,
    public val damage: Int,
    public val heat: Int,
    public val minimumRange: Int = 0,
    public val shortRange: Int,
    public val mediumRange: Int,
    public val longRange: Int,
    public val destroyed: Boolean = false,
    public val criticalSlots: Int = 1,
    public val ammoType: AmmoType? = null,
    public val location: MechLocation? = null,
    public val mountId: WeaponMountId? = null,
)
