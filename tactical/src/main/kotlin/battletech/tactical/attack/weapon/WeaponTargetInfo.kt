package battletech.tactical.attack.weapon

import battletech.tactical.dice.twoD6AtLeastProbability

public data class WeaponTargetInfo(
    val weaponIndex: Int,
    val weaponName: String,
    val targetDiceRoll: Int,
    val damage: Int,
    val modifiers: List<String>,
    val available: Boolean = true,
) {
    public val successChance: Int = twoD6AtLeastProbability(targetDiceRoll)
}
