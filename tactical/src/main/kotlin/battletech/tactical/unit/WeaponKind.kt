package battletech.tactical.unit

import kotlinx.serialization.Serializable

/**
 * How a weapon draws ammo and resolves its damage.
 *
 * **Known limitation (accepted under YAGNI):** this taxonomy conflates BattleTech's
 * weapon-class axis (energy/ballistic/missile) with its resolution-mechanism axis
 * (single-hit vs. Cluster Hits Table). Canon crosses those axes — an LB-X autocannon
 * is ballistic *and* cluster; a Thunderbolt missile is missile *and* single-hit.
 * Neither exists in this repo today. When one lands, splitting the axes is a
 * mechanical reshape of this hierarchy.
 */
@Serializable
public sealed interface WeaponKind {

    /** Weapons that draw rounds from an ammo bin. */
    @Serializable
    public sealed interface AmmoFed : WeaponKind {
        public val ammoType: AmmoType
    }

    /** No ammo bin — fires while intact (lasers, PPCs). */
    @Serializable
    public data object Energy : WeaponKind

    /** Ammo-fed, resolves to a single hit location (autocannons, machine guns). */
    @Serializable
    public data class Ballistic(override val ammoType: AmmoType) : AmmoFed

    /** Ammo-fed, resolves through the Cluster Hits Table (SRM/LRM). */
    @Serializable
    public data class Missile(
        override val ammoType: AmmoType,
        /**
         * Number of missiles per salvo. Resolution rolls the Cluster Hits Table for
         * missiles hit, then applies damage in groups using [damagePerMissile] and
         * [missilesPerGroup] instead of a flat per-attack damage value.
         */
        public val clusterSize: Int,
        /** Damage dealt per individual missile. */
        public val damagePerMissile: Int,
        /**
         * Number of missiles that share a single hit-location roll:
         * 1 for SRM (each missile rolls its own location), 5 for LRM (5-missile groups).
         */
        public val missilesPerGroup: Int,
    ) : AmmoFed {
        init {
            require(clusterSize > 0) { "clusterSize must be positive, was $clusterSize" }
            require(damagePerMissile > 0) { "damagePerMissile must be positive, was $damagePerMissile" }
            require(missilesPerGroup > 0) { "missilesPerGroup must be positive, was $missilesPerGroup" }
        }
    }
}
