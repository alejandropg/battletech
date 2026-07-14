package battletech.tactical.attack

import battletech.tactical.dice.DiceRoll
import battletech.tactical.unit.UnitId
import kotlinx.serialization.Serializable

/**
 * The outcome of resolving one weapon attack ([AttackDeclaration]) against the game state.
 *
 * Every attack rolls a to-hit check; [Miss] is the terminal outcome when that roll fails.
 * On a hit, [Hit] carries at least one [LocationHit] — [SingleHit] for ordinary weapons
 * (exactly one location) and [ClusterHit] for SRM/LRM-style weapons that roll a cluster
 * table first and can spread damage across several [locationHits] groups.
 */
@Serializable
public sealed interface AttackResult {
    public val attackerId: UnitId
    public val targetId: UnitId
    public val weaponName: String
    public val gunnery: Int
    public val modifiers: List<ToHitModifier>
    public val targetNumber: Int
    public val toHitRoll: DiceRoll
    public val rangeBand: RangeBand

    /** True when the target has partial cover (lower body masked by an obstacle).
     *  Leg-location hits under partial cover deal no damage and roll no crit. */
    public val partialCover: Boolean

    @Serializable
    public data class Miss(
        override val attackerId: UnitId,
        override val targetId: UnitId,
        override val weaponName: String,
        override val gunnery: Int,
        override val targetNumber: Int,
        override val toHitRoll: DiceRoll,
        override val rangeBand: RangeBand,
        override val modifiers: List<ToHitModifier> = emptyList(),
        override val partialCover: Boolean = false,
    ) : AttackResult

    /**
     * A successful to-hit roll. [locationHits] is non-empty by construction — one entry for
     * [SingleHit], one per connecting missile group for [ClusterHit].
     */
    @Serializable
    public sealed interface Hit : AttackResult {
        /** True when the attack struck the target's rear arc; rear torso hits use the rear armor track. */
        public val useRearArmor: Boolean

        /**
         * Per-group location hits, populated from the hit-location roll(s).
         *
         * For [SingleHit] this is a one-element list. For [ClusterHit] this contains one entry
         * per missile group, each with its own location roll and group damage. Pass-2 damage
         * resolution iterates this list so downstream code has a uniform shape.
         */
        public val locationHits: List<LocationHit>

        /** Damage steps actually applied (armor/IS/destroyed) — populated by pass-2 resolution via [withDamage]. */
        public val damage: List<LocationDamage>

        /** Total damage this attack dealt, summed from [locationHits]. */
        public val damageApplied: Int get() = locationHits.sumOf { it.damage }

        /** Returns a copy of this hit with [damage] replaced. */
        public fun withDamage(damage: List<LocationDamage>): Hit
    }

    @Serializable
    public data class SingleHit(
        override val attackerId: UnitId,
        override val targetId: UnitId,
        override val weaponName: String,
        override val gunnery: Int,
        override val targetNumber: Int,
        override val toHitRoll: DiceRoll,
        override val rangeBand: RangeBand,
        override val locationHits: List<LocationHit>,
        override val modifiers: List<ToHitModifier> = emptyList(),
        override val partialCover: Boolean = false,
        override val useRearArmor: Boolean = false,
        override val damage: List<LocationDamage> = emptyList(),
    ) : Hit {
        init {
            require(locationHits.isNotEmpty()) { "SingleHit requires a non-empty locationHits" }
        }

        override fun withDamage(damage: List<LocationDamage>): SingleHit = copy(damage = damage)
    }

    /**
     * Number of missiles that connected for cluster weapons (SRM/LRM). Used by the TUI to
     * render "LRM-20: 12 missiles → 5 CT, 5 RT, 2 LA".
     */
    @Serializable
    public data class ClusterHit(
        override val attackerId: UnitId,
        override val targetId: UnitId,
        override val weaponName: String,
        override val gunnery: Int,
        override val targetNumber: Int,
        override val toHitRoll: DiceRoll,
        override val rangeBand: RangeBand,
        override val locationHits: List<LocationHit>,
        val missilesHit: Int,
        override val modifiers: List<ToHitModifier> = emptyList(),
        override val partialCover: Boolean = false,
        override val useRearArmor: Boolean = false,
        override val damage: List<LocationDamage> = emptyList(),
    ) : Hit {
        init {
            require(locationHits.isNotEmpty()) { "ClusterHit requires a non-empty locationHits" }
        }

        override fun withDamage(damage: List<LocationDamage>): ClusterHit = copy(damage = damage)
    }
}
