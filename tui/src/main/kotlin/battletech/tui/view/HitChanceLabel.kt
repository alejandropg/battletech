package battletech.tui.view

import battletech.tactical.attack.ToHitBreakdown
import battletech.tui.icon.diceRoll

/**
 * Right-aligned hit-chance label: needed 2d6 target roll + success probability, e.g.
 * "<dice>7 58%". [targetDiceRoll] is floored to 2 for display — "you can never need less than
 * 2 on 2d6" is a statement about the dice about to be thrown, not about the rule, so
 * [ToHitBreakdown.targetNumber] itself stays unclamped.
 */
internal fun hitChanceLabel(targetDiceRoll: Int, successChance: Int): String =
    "${diceRoll()}${targetDiceRoll.coerceAtLeast(2)} $successChance%"

/** [hitChanceLabel] built directly from a [ToHitBreakdown]. */
internal fun hitChanceLabel(toHit: ToHitBreakdown): String =
    hitChanceLabel(toHit.targetNumber, toHit.successChance)
