package battletech.tui.game.phase

internal data class DeclaredTargetEntry(
    val targetName: String,
    val isPrimary: Boolean,
    val weapons: List<DeclaredWeaponEntry>,
)
