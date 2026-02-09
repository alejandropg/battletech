package battletech.tactical.model

public data class Weapon(
    val name: String,
    val damage: Int,
    val heat: Int,
    val minimumRange: Int = 0,
    val shortRange: Int,
    val mediumRange: Int,
    val longRange: Int,
    val ammo: Int? = null,
    val destroyed: Boolean = false,
)
