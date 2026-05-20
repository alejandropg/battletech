package battletech.tactical.session

import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.model.UnitId

/**
 * Reasons the session refuses a command before the underlying rules are
 * even consulted (phase/turn-ownership concerns). Wraps a [RuleRejection]
 * for failures that come from the rule layer.
 */
public sealed interface CommandRejection : RejectionReason {

    public data class NotYourTurn(public val activePlayer: PlayerId, public val attemptedBy: PlayerId) : CommandRejection

    public data class WrongPhase(public val actual: TurnPhase) : CommandRejection

    public data class UnitAlreadyActed(public val unitId: UnitId) : CommandRejection

    public data class UnknownUnit(public val unitId: UnitId) : CommandRejection

    public data object MatchOver : CommandRejection

    public data class RuleViolation(public val rule: RuleRejection) : CommandRejection
}
