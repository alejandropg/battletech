package battletech.tui.game.phase

internal data class DeclaredWeaponEntry(
    val weaponName: String,
    val successChance: Int,
    val targetDiceRoll: Int,
    val modifiers: List<String>,
)
