package battletech.tactical.session

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import battletech.tactical.unit.UnitId
import kotlinx.serialization.Serializable

/**
 * Typed reasons a tactical rule refuses an action. Each case carries the
 * structured data its description used to embed in a string, so deliveries
 * can render their own presentation without parsing.
 */
@Serializable
public sealed interface RuleRejection : RejectionReason {

    @Serializable
    public data class NotAdjacent(val distance: Int) : RuleRejection

    @Serializable
    public data class NoAmmo(val weaponName: String) : RuleRejection

    @Serializable
    public data class OutOfRange(
        val weaponName: String,
        val distance: Int,
        val maxRange: Int,
    ) : RuleRejection

    @Serializable
    public data class NoLineOfSight(
        val blockerAt: HexCoordinates,
        val blockingTerrain: Terrain,
    ) : RuleRejection

    @Serializable
    public data class WeaponDestroyed(val weaponName: String) : RuleRejection

    /** A unit tried to use the same limb for more than one physical attack this turn. */
    @Serializable
    public data class LimbAlreadyUsed(val attackerId: UnitId) : RuleRejection

    /** A unit tried to both punch and kick in the same turn (only one is allowed). */
    @Serializable
    public data class PunchAndKickSameTurn(val attackerId: UnitId) : RuleRejection

    /** A unit tried to make a physical attack with a destroyed (0 internal structure) limb. */
    @Serializable
    public data class LimbDestroyed(val attackerId: UnitId) : RuleRejection

    /** A unit tried to kick after running or jumping (kicks need walk/standing). */
    @Serializable
    public data object CannotKickAfterRunningOrJumping : RuleRejection

    /** A unit tried to punch after jumping (only a death-from-above is allowed from a jump). */
    @Serializable
    public data object CannotPunchAfterJumping : RuleRejection

    /** The target's level is out of reach for this physical attack. */
    @Serializable
    public data class ElevationOutOfReach(val levelDifference: Int) : RuleRejection

    /** The target is too deep in water to be struck by this physical attack. */
    @Serializable
    public data class TargetUnderwater(val depth: Int) : RuleRejection

    /** A prone unit tried to make a physical attack. */
    @Serializable
    public data object AttackerProne : RuleRejection

    /** The target unit has already been destroyed and cannot be attacked. */
    @Serializable
    public data object TargetDestroyed : RuleRejection

    /**
     * The attacker is fully submerged (water depth ≥ 2) and the weapon is not
     * [battletech.tactical.unit.Weapon.underwaterCapable]. Standard surface weapons
     * cannot fire from depth-2+ water.
     */
    @Serializable
    public data class AttackerSubmerged(val depth: Int) : RuleRejection
}
