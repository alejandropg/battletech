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

    /**
     * A command could not even be evaluated — e.g. it names a [battletech.tactical.unit.UnitId]
     * that does not exist ([battletech.tactical.unit.UnknownUnitException]). Distinct from
     * [Rejected]: a rejection is a legitimate outcome a correctly-behaving client can produce
     * and a UI should render; a [ProtocolError] means the command itself was malformed and can
     * only come from a bug or a tampered client. [BattleSession.submitCommand] never returns
     * this — it is produced only by the network adapter that catches the underlying exception
     * at the trust boundary (see [battletech.network.server.GameServer]), so a malformed remote
     * command gets a reply instead of tearing down the connection.
     */
    @Serializable
    public data class ProtocolError(public val message: String) : CommandResult
}
