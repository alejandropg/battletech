package battletech.network.wire

import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.CommandResult
import battletech.tactical.session.GameCommand
import battletech.tactical.session.LogEntry
import battletech.tactical.session.TurnState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire protocol version; bumped whenever [ClientMessage]/[ServerMessage] shapes change incompatibly. */
public const val PROTOCOL_VERSION: Int = 1

/**
 * A full read-only replica of session state as sent to a client: everything
 * [battletech.tactical.session.GameSession] exposes except [battletech.tactical.session.GameLog],
 * which travels separately as [LogEntry] deltas so the client can maintain its own log.
 */
@Serializable
public data class GameSnapshot(
    public val gameState: GameState,
    public val turnState: TurnState,
    public val currentPhase: TurnPhase,
    public val activePlayer: PlayerId?,
    public val isMatchOver: Boolean,
)

/**
 * Messages a client sends to the server. Polymorphic sealed encoding via
 * [WireJson]'s `type` class discriminator.
 */
@Serializable
public sealed interface ClientMessage {

    /** First message on a new connection: attempt to join [sessionId] speaking protocol [protocolVersion]. */
    @Serializable
    @SerialName("join")
    public data class Join(public val sessionId: String, public val protocolVersion: Int) : ClientMessage

    /** Submit [command] for processing; [requestId] correlates the eventual [ServerMessage.CommandReply]. */
    @Serializable
    @SerialName("submit")
    public data class SubmitCommand(public val requestId: Long, public val command: GameCommand) : ClientMessage
}

/** Why a [ClientMessage.Join] was refused. */
public enum class JoinRejectionReason {
    UNKNOWN_SESSION,
    INCOMPATIBLE_PROTOCOL,
    SEAT_TAKEN,
}

/**
 * Messages the server sends to a client. Polymorphic sealed encoding via
 * [WireJson]'s `type` class discriminator.
 *
 * **Ordering invariant:** for an accepted command, the server sends
 * [StatePush] BEFORE the corresponding [CommandReply]. A client applying
 * messages in the order it reads them (single reader thread) therefore
 * always has an up-to-date [GameSnapshot] by the time its blocked
 * `submitCommand` call returns — callers reading e.g. `currentPhase`
 * immediately after submit see the post-command phase. A rejected command
 * produces only a [CommandReply] (no [StatePush], since nothing changed).
 */
@Serializable
public sealed interface ServerMessage {

    /** Join succeeded: [playerId] is the seat assigned to the joiner, with a full [snapshot] and [log]. */
    @Serializable
    @SerialName("joinAccepted")
    public data class JoinAccepted(
        public val playerId: PlayerId,
        public val snapshot: GameSnapshot,
        public val log: List<LogEntry>,
    ) : ServerMessage

    /** Join refused for [reason]; the server keeps listening for further attempts. */
    @Serializable
    @SerialName("joinRejected")
    public data class JoinRejected(public val reason: JoinRejectionReason) : ServerMessage

    /**
     * Pushed after any accepted command (from either player): [entries] is the
     * game-log delta since the previous push, and [snapshot] is the resulting
     * full state. See the ordering invariant on [ServerMessage].
     */
    @Serializable
    @SerialName("push")
    public data class StatePush(public val entries: List<LogEntry>, public val snapshot: GameSnapshot) : ServerMessage

    /**
     * Correlates back to a [ClientMessage.SubmitCommand] by [requestId]. See
     * the ordering invariant on [ServerMessage]: for an accepted command this
     * arrives after the [StatePush] carrying the same change.
     */
    @Serializable
    @SerialName("reply")
    public data class CommandReply(public val requestId: Long, public val result: CommandResult) : ServerMessage
}
