package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tactical.attack.ToHitBreakdown
import io.archinaut.battletech.tactical.attack.ToHitModifier
import io.archinaut.battletech.tactical.attack.nonZero

/** Returns formatted modifier strings (e.g. "+2 med", "-1 range") for non-zero modifiers. */
internal fun List<ToHitModifier>.displayLabels(): List<String> =
    nonZero().map { "${if (it.amount > 0) "+" else ""}${it.amount} ${it.label}" }

/** Full to-hit breakdown as display strings: the base skill first (so the column sums to the
 *  target number), then each non-zero modifier. */
internal fun ToHitBreakdown.displayLabels(): List<String> =
    listOf("+$skill ${base.label}") + modifiers.displayLabels()
