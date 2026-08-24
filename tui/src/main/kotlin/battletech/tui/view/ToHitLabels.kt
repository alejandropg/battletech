package battletech.tui.view

import battletech.tactical.attack.ToHitModifier
import battletech.tactical.attack.nonZero

/** Returns formatted modifier strings (e.g. "+2 med", "-1 range") for non-zero modifiers. */
internal fun List<ToHitModifier>.displayLabels(): List<String> =
    nonZero().map { "${if (it.amount > 0) "+" else ""}${it.amount} ${it.label}" }

/** Full to-hit breakdown as display strings: the gunnery base first (so the column sums to the
 *  target number), then each non-zero modifier. */
internal fun toHitBreakdownLabels(gunnery: Int, modifiers: List<ToHitModifier>): List<String> =
    listOf("+$gunnery gunnery") + modifiers.displayLabels()
