package battletech.tactical.query

import battletech.tactical.unit.UnitId

/**
 * One weapon's committed declaration within a [DeclaredWeaponAttack]: its index on the
 * attacker, a display name, the target number and success chance it would resolve at right
 * now, and the modifier labels that sum to it (rendered verbatim, e.g. "+2 range").
 */
public data class DeclaredWeaponLine(
    public val weaponIndex: Int,
    public val weaponName: String,
    public val targetNumber: Int,
    public val successChance: Int,
    public val modifierLabels: List<String>,
)

/**
 * A committed weapon-attack declaration for one (attacker, target) pair: whether [targetId] is
 * the attacker's primary target this impulse, and the [weapons] declared against it.
 */
public data class DeclaredWeaponAttack(
    public val attackerId: UnitId,
    public val targetId: UnitId,
    public val isPrimary: Boolean,
    public val weapons: List<DeclaredWeaponLine>,
)
