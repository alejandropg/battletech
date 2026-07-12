package battletech.tactical.session

import kotlinx.serialization.Serializable

/**
 * Outcome of submitting a [GameCommand] to a session. Subscribers receive
 * the same events via the subscription channel; the submitter gets
 * them synchronously here as a courtesy.
 */
@Serializable
public sealed interface CommandResult {

    @Serializable
    public data class Accepted(public val events: List<GameEvent>) : CommandResult

    @Serializable
    public data class Rejected(public val reason: RejectionReason) : CommandResult
}
