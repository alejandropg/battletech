package battletech.tactical.unit

public data class WeaponModel(
    public val name: String,
    public val damage: Int,
    public val heat: Int,
    public val minimumRange: Int = 0,
    public val shortRange: Int,
    public val mediumRange: Int,
    public val longRange: Int,
    public val criticalSlots: Int = 1,
    public val ammoType: AmmoType? = null,
)
