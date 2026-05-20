package battletech.tactical.session



/**
 * Outcome of submitting a [GameCommand] to a session. Subscribers receive
 * the same events via the subscription channel; the submitter gets
 * them synchronously here as a courtesy.
 */
public sealed interface CommandResult {

    public data class Accepted(public val events: List<GameEvent>) : CommandResult

    public data class Rejected(public val reason: RejectionReason) : CommandResult
}
