package battletech.tactical.query

import battletech.tactical.unit.UnitId

/**
 * One weapon's committed declaration within a [DeclaredWeaponAttack].
 *
 * Split by what the viewer may legitimately know, mirroring the
 * [battletech.tactical.unit.CombatUnit]/[battletech.tactical.unit.ForeignUnit] and
 * [battletech.tactical.session.CriticalHit] `Detailed`/`Undisclosed` precedents: WHICH
 * weapons are pointed at WHICH target is observable (you watch the torso
 * swing), but the attacker's to-hit MATH is not — it is computed from the attacker's gunnery
 * skill, current heat, and sensor criticals, all record-sheet data. So the prediction rides
 * only on [Detailed], for attackers the viewer owns; a foreign attacker's declaration
 * arrives as [Undisclosed], carrying the observable fact and nothing more.
 *
 * There is deliberately no "unknown" target number: a sentinel like 13/0% would assert a
 * falsehood rather than withhold a truth.
 */
public sealed interface DeclaredWeaponLine {
    public val weaponIndex: Int
    public val weaponName: String

    /**
     * A weapon on an attacker the viewer owns: carries [targetNumber], [successChance], and
     * the [modifierLabels] that sum to it (rendered verbatim, e.g. "+2 range").
     */
    public data class Detailed(
        override val weaponIndex: Int,
        override val weaponName: String,
        public val targetNumber: Int,
        public val successChance: Int,
        public val modifierLabels: List<String>,
    ) : DeclaredWeaponLine

    /**
     * A weapon on an attacker the viewer does NOT own: the weapon and its target are
     * observable, the to-hit prediction is not. There is no target-number field on this type,
     * so leaking one is a compile error rather than a discipline problem.
     */
    public data class Undisclosed(
        override val weaponIndex: Int,
        override val weaponName: String,
    ) : DeclaredWeaponLine
}

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
