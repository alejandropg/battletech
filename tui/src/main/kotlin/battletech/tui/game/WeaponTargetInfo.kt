package battletech.tui.game

public data class WeaponTargetInfo(
    val weaponIndex: Int,
    val weaponName: String,
    val successChance: Int,
    val damage: Int,
    val modifiers: List<String>,
    val available: Boolean = true,
)
