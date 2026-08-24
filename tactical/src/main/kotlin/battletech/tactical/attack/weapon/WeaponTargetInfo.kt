package battletech.tactical.attack.weapon

import battletech.tactical.attack.ToHitModifier
import battletech.tactical.dice.twoD6AtLeastProbability

/**
 * [gunnery] is the attacker's base skill this target number was built from, or null when no
 * skill-based breakdown applies (e.g. a physical-attack option, which is never built with
 * [modifiers] populated either) — deliveries compose the two into a display breakdown
 * themselves; this type carries only the rule data.
 */
public data class WeaponTargetInfo(
    val weaponIndex: Int,
    val weaponName: String,
    val targetDiceRoll: Int,
    val damage: Int,
    val modifiers: List<ToHitModifier>,
    val gunnery: Int? = null,
    val available: Boolean = true,
) {
    public val successChance: Int = twoD6AtLeastProbability(targetDiceRoll)
}
