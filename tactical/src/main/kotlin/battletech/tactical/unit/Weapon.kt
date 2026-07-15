package battletech.tactical.unit

import battletech.tactical.model.MechLocation
import kotlinx.serialization.Serializable

@Serializable
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
    public val kind: WeaponKind get() = model.kind
    public val ammoType: AmmoType? get() = (model.kind as? WeaponKind.AmmoFed)?.ammoType
    public val underwaterCapable: Boolean get() = model.underwaterCapable
}
