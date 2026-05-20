package battletech.tactical.session

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain

/**
 * Typed reasons a tactical rule refuses an action. Each case carries the
 * structured data its description used to embed in a string, so deliveries
 * can render their own presentation without parsing.
 */
public sealed interface RuleRejection : RejectionReason {

    public data class NotAdjacent(val distance: Int) : RuleRejection

    public data class NoAmmo(val weaponName: String) : RuleRejection

    public data class OutOfRange(
        val weaponName: String,
        val distance: Int,
        val maxRange: Int,
    ) : RuleRejection

    public data class NoLineOfSight(
        val blockerAt: HexCoordinates,
        val blockingTerrain: Terrain,
    ) : RuleRejection

    public data class WeaponDestroyed(val weaponName: String) : RuleRejection
}
