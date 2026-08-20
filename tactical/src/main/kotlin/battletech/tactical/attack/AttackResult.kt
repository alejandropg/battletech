package battletech.tactical.attack

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
public sealed interface AttackResult : ToHitAttempted {
    /** Who shot what, with what weapon, and the to-hit check that decided this outcome. */
    public val attempt: ToHitAttempt

    @Serializable
    public data class Miss(
        override val attempt: ToHitAttempt,
    ) : AttackResult, ToHitAttempted by attempt

    /**
     * A successful to-hit roll. [locationHits] is non-empty by construction — one entry for
     * [SingleHit], one per connecting missile group for [ClusterHit].
     */
    @Serializable
    public sealed interface Hit : AttackResult, ResolvedAttack {
        /** True when the target has partial cover (lower body masked by an obstacle).
         *  Leg-location hits under partial cover deal no damage and roll no crit. */
        public val partialCover: Boolean

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

        /** Total damage this attack dealt, summed from [locationHits]. */
        public val damageApplied: Int get() = locationHits.sumOf { it.damage }

        /** Returns a copy of this hit with [damage] replaced. */
        public fun withDamage(damage: List<LocationDamage>): Hit
    }

    @Serializable
    public data class SingleHit(
        override val attempt: ToHitAttempt,
        override val locationHits: List<LocationHit>,
        override val partialCover: Boolean = false,
        override val useRearArmor: Boolean = false,
        override val damage: List<LocationDamage> = emptyList(),
    ) : Hit, ToHitAttempted by attempt {
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
        override val attempt: ToHitAttempt,
        override val locationHits: List<LocationHit>,
        val missilesHit: Int,
        override val partialCover: Boolean = false,
        override val useRearArmor: Boolean = false,
        override val damage: List<LocationDamage> = emptyList(),
    ) : Hit, ToHitAttempted by attempt {
        init {
            require(locationHits.isNotEmpty()) { "ClusterHit requires a non-empty locationHits" }
        }

        override fun withDamage(damage: List<LocationDamage>): ClusterHit = copy(damage = damage)
    }
}
