package battletech.tactical.attack

/**
 * A single named contribution to a weapon or physical attack's to-hit target number
 * (e.g. range, heat, secondary target, prone target). Negative [amount]s are bonuses.
 */
public data class ToHitModifier(public val label: String, public val amount: Int)

/** Sums all modifier amounts into the total adjustment to the base gunnery skill. */
public fun List<ToHitModifier>.total(): Int = sumOf { it.amount }

/** Filters out modifiers that contribute nothing, for display purposes. */
public fun List<ToHitModifier>.nonZero(): List<ToHitModifier> = filter { it.amount != 0 }
