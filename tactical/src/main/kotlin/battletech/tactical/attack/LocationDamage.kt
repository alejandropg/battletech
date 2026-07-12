package battletech.tactical.attack

import kotlinx.serialization.Serializable

/**
 * One location's worth of damage resolution, as produced by [resolveDamage].
 * Multiple [LocationDamage] steps are produced when damage transfers inward
 * after a location's internal structure is destroyed.
 */
@Serializable
public data class LocationDamage(
    val location: HitLocation,
    val armorDamage: Int,
    val structureDamage: Int,
    val destroyed: Boolean,
)
