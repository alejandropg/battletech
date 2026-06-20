package battletech.tactical.attack.weapon

public data class WeaponTargetInfo(
    val weaponIndex: Int,
    val weaponName: String,
    val successChance: Int,
    val damage: Int,
    val modifiers: List<String>,
    val targetDiceRoll: Int,
    val available: Boolean = true,
)
