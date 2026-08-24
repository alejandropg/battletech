package battletech.tactical.rules

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import battletech.tactical.unit.UnitId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typed reasons a tactical rule refuses an action. Each case carries the
 * structured data its description used to embed in a string, so deliveries
 * can render their own presentation without parsing.
 *
 * Deliberately does NOT implement [battletech.tactical.session.RejectionReason] — see that
 * interface's KDoc for why. A [RuleRejection] reaches a client only wrapped inside
 * [battletech.tactical.session.CommandRejection.RuleViolation].
 */
@Serializable
public sealed interface RuleRejection {

    @Serializable
    @SerialName("notAdjacent")
    public data class NotAdjacent(val distance: Int) : RuleRejection

    @Serializable
    @SerialName("noAmmo")
    public data class NoAmmo(val weaponName: String) : RuleRejection

    @Serializable
    @SerialName("outOfRange")
    public data class OutOfRange(
        val weaponName: String,
        val distance: Int,
        val maxRange: Int,
    ) : RuleRejection

    @Serializable
    @SerialName("noLineOfSight")
    public data class NoLineOfSight(
        val blockerAt: HexCoordinates,
        val blockingTerrain: Terrain,
    ) : RuleRejection

    @Serializable
    @SerialName("weaponDestroyed")
    public data class WeaponDestroyed(val weaponName: String) : RuleRejection

    /** A unit tried to use the same limb for more than one physical attack this turn. */
    @Serializable
    @SerialName("limbAlreadyUsed")
    public data class LimbAlreadyUsed(val attackerId: UnitId) : RuleRejection

    /** A unit tried to both punch and kick in the same turn (only one is allowed). */
    @Serializable
    @SerialName("punchAndKickSameTurn")
    public data class PunchAndKickSameTurn(val attackerId: UnitId) : RuleRejection

    /** A unit tried to make a physical attack with a destroyed (0 internal structure) limb. */
    @Serializable
    @SerialName("limbDestroyed")
    public data class LimbDestroyed(val attackerId: UnitId) : RuleRejection

    /** A unit tried to kick after running or jumping (kicks need walk/standing). */
    @Serializable
    @SerialName("cannotKickAfterRunningOrJumping")
    public data object CannotKickAfterRunningOrJumping : RuleRejection

    /** A unit tried to punch after jumping (only a death-from-above is allowed from a jump). */
    @Serializable
    @SerialName("cannotPunchAfterJumping")
    public data object CannotPunchAfterJumping : RuleRejection

    /** The target's level is out of reach for this physical attack. */
    @Serializable
    @SerialName("elevationOutOfReach")
    public data class ElevationOutOfReach(val levelDifference: Int) : RuleRejection

    /** The target is too deep in water to be struck by this physical attack. */
    @Serializable
    @SerialName("targetUnderwater")
    public data class TargetUnderwater(val depth: Int) : RuleRejection

    /** A prone unit tried to make a physical attack. */
    @Serializable
    @SerialName("attackerProne")
    public data object AttackerProne : RuleRejection

    /** The target unit has already been destroyed and cannot be attacked. */
    @Serializable
    @SerialName("targetDestroyed")
    public data object TargetDestroyed : RuleRejection

    /**
     * The attacker is fully submerged (water depth ≥ 2) and the weapon is not
     * [battletech.tactical.unit.Weapon.underwaterCapable]. Standard surface weapons
     * cannot fire from depth-2+ water.
     */
    @Serializable
    @SerialName("attackerSubmerged")
    public data class AttackerSubmerged(val depth: Int) : RuleRejection
}
