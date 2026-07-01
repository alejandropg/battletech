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

/** Returns formatted modifier strings (e.g. "+2 med", "-1 range") for non-zero modifiers. */
public fun List<ToHitModifier>.displayLabels(): List<String> =
    nonZero().map { (label, amount) -> "${if (amount > 0) "+" else ""}$amount $label" }

/** Full to-hit breakdown as display strings: the gunnery base first (so the column sums to the
 *  target number), then each non-zero modifier. */
public fun toHitBreakdownLabels(gunnery: Int, modifiers: List<ToHitModifier>): List<String> =
    listOf("+$gunnery gunnery") + modifiers.displayLabels()
