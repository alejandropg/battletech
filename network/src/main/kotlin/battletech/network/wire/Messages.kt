package battletech.network.wire

import battletech.tactical.model.GameMap
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.model.content.AssetBundle
import battletech.tactical.model.content.ContentSummary
import battletech.tactical.model.content.MatchPlan
import battletech.tactical.session.CommandResult
import battletech.tactical.session.GameCommand
import battletech.tactical.session.LogEntry
import battletech.tactical.session.TurnState
import battletech.tactical.unit.VisibleUnit
import battletech.tactical.unit.UnitRoster
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire protocol version; bumped whenever [ClientMessage]/[ServerMessage] shapes change incompatibly. */
public const val PROTOCOL_VERSION: Int = 10

/**
 * A read-only replica of session state as sent to a client: everything
 * [battletech.tactical.session.GameSession] exposes except [battletech.tactical.session.GameLog]
 * (which travels separately as [LogEntry] deltas so the client can maintain its own log) and the
 * board ([GameMap]), which travels exactly once, in [MatchBootstrap.map] — the board is immutable
 * for a match, so re-sending it in every [ServerMessage.StatePush] would be pure redundant bytes
 * on the wire. A [ClientGameSession][battletech.network.client.ClientGameSession] reassembles
 * [battletech.tactical.query.PlayerGameState] from that join-time map plus [units].
 *
 * [units] is the per-viewer projection ([battletech.tactical.query.projectFor]) built by
 * [battletech.network.server.GameServer.snapshotFor] for the specific seat this snapshot is
 * addressed to. See [battletech.network.server.GameServer] for the other two outbound paths
 * ([ServerMessage.StatePush]'s log delta and [MatchBootstrap.log]) that must
 * redact in lockstep with this snapshot.
 *
 * [turnState] is NOT filtered per seat: [battletech.tactical.session.AttackProgress]'s
 * `weaponDeclarations`/`physicalDeclarations` hold both players' committed declarations,
 * but declared targets are the torso swinging toward its target — observable at the table
 * — and [battletech.tactical.query.PlayerView.declaredWeaponAttacks] already returns both
 * sides' commitments by design (see its KDoc). Nothing else in [TurnState] carries
 * record-sheet data, so it travels as-is.
 */
@Serializable
public data class GameSnapshot(
    public val units: UnitRoster<VisibleUnit>,
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

    /**
     * First message on a new connection: attempt to join [sessionId] speaking protocol
     * [protocolVersion], contributing [content] — everything this seat has registered
     * (built-ins and `--add-*` externals alike) — to the match's shared [AssetRegistry].
     * [content] is defaulted (rather than required) because [protocolVersion] travels inside
     * this same message: an old client's old-shape `Join` must still decode far enough for the
     * server to answer [JoinRejectionReason.INCOMPATIBLE_PROTOCOL] instead of throwing in the
     * decoder.
     */
    @Serializable
    @SerialName("join")
    public data class Join(
        public val sessionId: String,
        public val protocolVersion: Int,
        public val content: AssetBundle = AssetBundle.EMPTY,
    ) : ClientMessage

    /** Submit [command] for processing; [requestId] correlates the eventual [ServerMessage.CommandReply]. */
    @Serializable
    @SerialName("submitCommand")
    public data class SubmitCommand(public val requestId: Long, public val command: GameCommand) : ClientMessage
}

/** Why a [ClientMessage.Join] was refused. */
public enum class JoinRejectionReason {
    UNKNOWN_SESSION,
    INCOMPATIBLE_PROTOCOL,
    SEAT_TAKEN,

    /** [ClientMessage.Join.content] repeats an id within one [battletech.tactical.model.content.AssetKind]. */
    INVALID_CONTENT,
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
    /**
     * Join succeeded with one complete [bootstrap]. Its map and mech definitions travel only
     * here; later state changes use [StatePush].
     */
    @Serializable
    @SerialName("joinAccepted")
    public data class JoinAccepted(
        public val bootstrap: MatchBootstrap,
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
    @SerialName("statePush")
    public data class StatePush(public val entries: List<LogEntry>, public val snapshot: GameSnapshot) : ServerMessage

    /**
     * Correlates back to a [ClientMessage.SubmitCommand] by [requestId]. See
     * the ordering invariant on [ServerMessage]: for an accepted command this
     * arrives after the [StatePush] carrying the same change.
     */
    @Serializable
    @SerialName("commandReply")
    public data class CommandReply(public val requestId: Long, public val result: CommandResult) : ServerMessage

    /**
     * Sent once, right after a [ClientMessage.Join] parks (the match is not yet committed): the
     * merged catalog the setup screen's panels list from, including this joiner's own
     * `--add-map`/`--add-mech` contribution.
     */
    @Serializable
    @SerialName("lobbyJoined")
    public data class LobbyJoined(public val catalog: ContentSummary) : ServerMessage

    /** Mirrors the host's current selections to a parked joiner — resent on every host change, no debounce. */
    @Serializable
    @SerialName("lobbySelections")
    public data class LobbySelections(public val plan: MatchPlan) : ServerMessage

    /** The host committed. The parked joiner re-sends its original [ClientMessage.Join] next — see `docs/wire-protocol.md`. */
    @Serializable
    @SerialName("lobbyCommitted")
    public data object LobbyCommitted : ServerMessage
}
