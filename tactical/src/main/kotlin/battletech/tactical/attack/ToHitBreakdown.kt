package battletech.tactical.attack

import battletech.tactical.dice.twoD6AtLeastProbability
import kotlinx.serialization.Serializable

/**
 * Which record-sheet skill a [ToHitBreakdown] builds on: weapon fire adds [ToHitModifier]s to
 * gunnery, physical attacks to piloting. [label] is the rule-book noun, kept for display
 * exactly as [ToHitModifier.label] is — sign, spacing and casing stay a delivery concern.
 */
@Serializable
public enum class ToHitBase(public val label: String) {
    GUNNERY("gunnery"),
    PILOTING("piloting"),
}

/**
 * One complete to-hit prediction: the attacker's base [skill] and the named [modifiers] that
 * adjust it. [targetNumber] and [successChance] are DERIVED, so no caller can pair a target
 * number with a breakdown that doesn't sum to it — which is precisely what a loose
 * `gunnery: Int?` beside a `List<ToHitModifier>` beside a separately-stored target number
 * allowed. Body properties, so they don't serialize.
 *
 * Deliberately unclamped: [targetNumber] is exactly the number attack resolution compares the
 * 2d6 roll against. The "you can never need less than 2" display floor lives in the delivery
 * ([battletech.tui.view.hitChanceLabel]); [successChance] needs no floor because
 * [twoD6AtLeastProbability] already saturates at 100 below 2 and 0 at 13+.
 */
@Serializable
public data class ToHitBreakdown(
    public val base: ToHitBase,
    public val skill: Int,
    public val modifiers: List<ToHitModifier>,
) {
    public val targetNumber: Int = skill + modifiers.total()
    public val successChance: Int = twoD6AtLeastProbability(targetNumber)
}
