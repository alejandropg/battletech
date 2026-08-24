package battletech.tactical.attack

import kotlinx.serialization.Serializable

/**
 * The rule-book category a [ToHitModifier] belongs to. Lets consumers (resolution,
 * targeting prediction) pick out a specific modifier without matching on its display
 * [ToHitModifier.label], which is free-text and only meant for rendering.
 */
@Serializable
public enum class ToHitFactor {
    RANGE,
    HEAT,
    SECONDARY_TARGET,
    PRONE_TARGET,
    IMMOBILE_TARGET,
    SENSORS,
    ATTACKER_MOVEMENT,
    TARGET_MOVEMENT,
    MINIMUM_RANGE,
    TERRAIN,
    ATTACK_KIND,
}

/**
 * A single named contribution to a weapon or physical attack's to-hit target number
 * (e.g. range, heat, secondary target, prone target). Negative [amount]s are bonuses.
 * [factor] is the typed category for lookups; [label] is kept purely for display
 * (e.g. TUI log/targets panels) and is free to vary within a factor (range labels are
 * "short"/"med"/"long"/"out of range"; attack-kind labels are "punch"/"kick").
 */
@Serializable
public data class ToHitModifier(public val factor: ToHitFactor, public val label: String, public val amount: Int)

/** Sums all modifier amounts into the total adjustment to the base gunnery skill. */
public fun List<ToHitModifier>.total(): Int = sumOf { it.amount }

/** Returns the amount of the single modifier for [factor]. Throws if none is present. */
public fun List<ToHitModifier>.amountOf(factor: ToHitFactor): Int = first { it.factor == factor }.amount

/** Filters out modifiers that contribute nothing, for display purposes. */
public fun List<ToHitModifier>.nonZero(): List<ToHitModifier> = filter { it.amount != 0 }
