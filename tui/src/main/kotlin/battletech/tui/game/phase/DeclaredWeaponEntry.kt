package battletech.tui.game.phase

/**
 * One weapon line in the DECLARED TARGETS panel, mirroring
 * [battletech.tactical.query.DeclaredWeaponLine]'s split (see its KDoc for the rule):
 * WHICH weapon is aimed at WHICH target is observable, but the attacker's to-hit math is
 * record-sheet data, so it exists only on [Detailed] — the viewer's own attackers.
 *
 * [Undisclosed] has no target-number/modifier field at all, so
 * [battletech.tui.view.DeclaredTargetsView] cannot print an enemy's to-hit breakdown even by
 * mistake.
 */
internal sealed interface DeclaredWeaponEntry {
    val weaponName: String

    /** A weapon on an attacker the viewer owns: full to-hit prediction shown. */
    data class Detailed(
        override val weaponName: String,
        val successChance: Int,
        val targetDiceRoll: Int,
        val modifiers: List<String>,
    ) : DeclaredWeaponEntry

    /** A weapon on an enemy attacker: the declaration is shown, the prediction withheld. */
    data class Undisclosed(
        override val weaponName: String,
    ) : DeclaredWeaponEntry
}
