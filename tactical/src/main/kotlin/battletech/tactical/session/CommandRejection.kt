package battletech.tactical.session

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.rules.RuleRejection
import battletech.tactical.unit.UnitId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reasons the session refuses a command before the underlying rules are
 * even consulted (phase/turn-ownership concerns). Wraps a [RuleRejection]
 * for failures that come from the rule layer.
 */
@Serializable
public sealed interface CommandRejection : RejectionReason {

    @Serializable
    @SerialName("notYourTurn")
    public data class NotYourTurn(public val activePlayer: PlayerId, public val attemptedBy: PlayerId) : CommandRejection

    /**
     * A command referenced a unit not owned by [attemptedBy]. Distinct from [NotYourTurn]
     * (the session-level active-player check in [BattleSession.submitCommand]): this is a
     * per-unit ownership check a handler runs on units named inside its own command payload
     * (e.g. a torso-facing entry, or an attacker/declaration that isn't the impulse's overall
     * active player but is still named by the command).
     */
    @Serializable
    @SerialName("notYourUnit")
    public data class NotYourUnit(public val unitId: UnitId, public val owner: PlayerId, public val attemptedBy: PlayerId) : CommandRejection

    @Serializable
    @SerialName("wrongPhase")
    public data class WrongPhase(public val actual: TurnPhase) : CommandRejection

    @Serializable
    @SerialName("unitAlreadyActed")
    public data class UnitAlreadyActed(public val unitId: UnitId) : CommandRejection

    /** A prone unit was told to move; it must stand up first. */
    @Serializable
    @SerialName("unitProne")
    public data class UnitProne(public val unitId: UnitId) : CommandRejection

    /** A non-prone unit was told to stand up. */
    @Serializable
    @SerialName("unitNotProne")
    public data class UnitNotProne(public val unitId: UnitId) : CommandRejection

    /** A unit with a destroyed gyro (2+ crits) can never stand up again. */
    @Serializable
    @SerialName("gyroDestroyed")
    public data class GyroDestroyed(public val unitId: UnitId) : CommandRejection

    /** A unit with a destroyed leg may not jump or run. */
    @Serializable
    @SerialName("legDestroyed")
    public data class LegDestroyed(public val unitId: UnitId) : CommandRejection

    /** The requested weapon index does not exist on the unit's weapon list. */
    @Serializable
    @SerialName("noSuchWeapon")
    public data class NoSuchWeapon(public val unitId: UnitId, public val weaponIndex: Int) : CommandRejection

    /** The declared target is owned by the same player as the attacker. */
    @Serializable
    @SerialName("friendlyFire")
    public data class FriendlyFire(public val targetId: UnitId) : CommandRejection

    /**
     * The requested torso facing is more than one hex-side away from the unit's
     * leg facing; torso twists are limited to ±1 facing step.
     */
    @Serializable
    @SerialName("illegalTorsoTwist")
    public data class IllegalTorsoTwist(public val unitId: UnitId, public val facing: HexDirection) : CommandRejection

    /**
     * The requested movement destination is not reachable by the unit for the
     * given movement mode, or the client-supplied path/mpSpent does not match
     * the server-authoritative reachability computation.
     */
    @Serializable
    @SerialName("destinationUnreachable")
    public data class DestinationUnreachable(public val unitId: UnitId, public val destination: HexCoordinates) : CommandRejection

    @Serializable
    @SerialName("matchOver")
    public data object MatchOver : CommandRejection

    @Serializable
    @SerialName("ruleViolation")
    public data class RuleViolation(public val rule: RuleRejection) : CommandRejection

    /** Rejected because no opponent is currently connected. */
    @Serializable
    @SerialName("opponentUnavailable")
    public data object OpponentUnavailable : CommandRejection
}
