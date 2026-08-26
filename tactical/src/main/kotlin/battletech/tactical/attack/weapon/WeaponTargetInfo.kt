package battletech.tactical.attack.weapon

import battletech.tactical.attack.ToHitBreakdown

/**
 * One weapon's line against one [TargetInfo]. Split by whether the weapon can actually fire:
 * eligibility used to be encoded four correlated ways at once (`available = false`, a `13`
 * target-number sentinel, `emptyList()` modifiers, `gunnery = null`) that nothing kept in
 * agreement. Now it is one type choice, and [Unavailable] has no to-hit field to fake a
 * prediction into — the same discipline as [battletech.tactical.query.DeclaredWeaponLine].
 */
public sealed interface WeaponTargetInfo {
    public val weaponIndex: Int
    public val weaponName: String
    public val damage: Int

    /** Passes every [FireWeaponActionDefinition] rule: full prediction attached. */
    public data class Available(
        override val weaponIndex: Int,
        override val weaponName: String,
        override val damage: Int,
        public val toHit: ToHitBreakdown,
    ) : WeaponTargetInfo

    /** Out of range, no ammo, destroyed, submerged or no LOS — listed so the panel can grey
     *  the row, carrying no to-hit math because none exists. */
    public data class Unavailable(
        override val weaponIndex: Int,
        override val weaponName: String,
        override val damage: Int,
    ) : WeaponTargetInfo
}
