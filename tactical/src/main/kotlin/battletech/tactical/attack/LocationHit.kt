package battletech.tactical.attack

import battletech.tactical.dice.DiceRoll
import kotlinx.serialization.Serializable

/**
 * One group's resolved hit for a cluster or single-location attack.
 *
 * For cluster weapons (SRM/LRM): each missile group that connects gets its own [LocationHit]
 * with its own 2d6 location roll. For standard single-location weapons there is exactly one
 * [LocationHit] per attack (when it hits).
 */
@Serializable
public data class LocationHit(
    /** Location struck by this group. */
    val location: HitLocation,
    /** Damage this group deals (groupSize × damagePerMissile, or weapon.damage for non-cluster). */
    val damage: Int,
    /** The 2d6 roll that selected this location. */
    val locationRoll: DiceRoll,
)
