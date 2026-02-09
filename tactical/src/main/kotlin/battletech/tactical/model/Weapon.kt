package battletech.tactical.model

public data class Weapon(
    public val name: String,
    public val damage: Int,
    public val heat: Int,
    public val minimumRange: Int = 0,
    public val shortRange: Int,
    public val mediumRange: Int,
    public val longRange: Int,
    public val ammo: Int? = null,
    public val destroyed: Boolean = false,
)
