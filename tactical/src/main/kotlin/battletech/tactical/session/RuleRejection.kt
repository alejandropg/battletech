package battletech.tactical.session

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import battletech.tactical.unit.UnitId

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

    /** A unit tried to use the same limb for more than one physical attack this turn. */
    public data class LimbAlreadyUsed(val attackerId: UnitId) : RuleRejection

    /** A unit tried to both punch and kick in the same turn (only one is allowed). */
    public data class PunchAndKickSameTurn(val attackerId: UnitId) : RuleRejection

    /** A unit tried to make a physical attack with a destroyed (0 internal structure) limb. */
    public data class LimbDestroyed(val attackerId: UnitId) : RuleRejection

    /** A unit tried to kick after running or jumping (kicks need walk/standing). */
    public data object CannotKickAfterRunningOrJumping : RuleRejection

    /** A unit tried to punch after jumping (only a death-from-above is allowed from a jump). */
    public data object CannotPunchAfterJumping : RuleRejection

    /** The target's level is out of reach for this physical attack. */
    public data class ElevationOutOfReach(val levelDifference: Int) : RuleRejection

    /** The target is too deep in water to be struck by this physical attack. */
    public data class TargetUnderwater(val depth: Int) : RuleRejection
}
