package battletech.network.server

import battletech.network.transport.ServerConnection
import battletech.network.wire.ClientMessage
import battletech.network.wire.JoinRejectionReason
import battletech.network.wire.PROTOCOL_VERSION
import battletech.network.wire.ServerMessage
import battletech.network.wire.SessionId
import battletech.tactical.model.GameState
import battletech.tactical.model.content.AssetBundle
import battletech.tactical.model.content.AssetRegistry
import battletech.tactical.model.content.MatchPlan
import battletech.tactical.model.content.summarize

/**
 * The pre-match half of hosting: accepts a join before a [GameState] exists, merges the joiner's
 * [AssetBundle] into the registry the host is still choosing from, mirrors the host's [MatchPlan]
 * to the parked peer, and at [commit] builds the [GameServer] and hands the parked connection to
 * it.
 *
 * There is exactly one path in, for every mode: hot-seat constructs this with no [SocketAcceptor]
 * and commits immediately at the user's keystroke; `host` adds a [SocketAcceptor]; `server` adds
 * one and commits at startup. Nothing downstream — [GameServer], [ClientMessage.Join] handling —
 * can tell those apart.
 *
 * Only one peer can ever be parked here (a two-player match has exactly one seat left to fill
 * once the host itself is accounted for): a second [ClientMessage.Join] while one is already
 * parked is rejected [JoinRejectionReason.SEAT_TAKEN], the same answer [GameServer] gives once
 * every seat is full.
 */
/**
 * One [LobbyHost.onChange] notification: the merged [registry] as it now stands, and whether a
 * peer is parked right now. Both travel together because a listener that only ever heard "the
 * registry changed" cannot tell an arrival from a departure — the setup screen needs to, since a
 * departure re-closes the commit gate.
 */
public data class LobbyStatus(
    public val registry: AssetRegistry,
    public val opponentConnected: Boolean,
)

public class LobbyHost(
    public val sessionId: String = SessionId.generate(),
    ownContent: AssetBundle = AssetBundle.EMPTY,
) : AutoCloseable {

    private val lock: Any = Any()
    private var mergedRegistry: AssetRegistry = AssetRegistry.EMPTY.merge(ownContent).registry
    private var parked: ServerConnection? = null
    private var gameServer: GameServer? = null
    private val listeners: MutableList<(LobbyStatus) -> Unit> = mutableListOf()

    /** The merged registry as it stands right now — read under the lock. */
    public val registry: AssetRegistry get() = synchronized(lock) { mergedRegistry }

    public val opponentConnected: Boolean get() = synchronized(lock) { parked != null }

    /**
     * Fired whenever a peer parks OR the parked peer drops — [LobbyStatus.opponentConnected] says
     * which, so a listener cannot mistake a departure for an arrival. Listeners are held under
     * this class's own lock and invoked outside it: they run on the parked connection's reader
     * thread, not the registering thread.
     */
    public fun onChange(listener: (LobbyStatus) -> Unit) {
        synchronized(lock) { listeners += listener }
    }

    private fun notifyChange() {
        val (snapshot, status) = synchronized(lock) {
            listeners.toList() to LobbyStatus(mergedRegistry, parked != null)
        }
        snapshot.forEach { it(status) }
    }

    /** Mirrors [plan] to the parked peer, if any. A no-op with nobody parked. */
    public fun publish(plan: MatchPlan) {
        synchronized(lock) { parked }?.send(ServerMessage.LobbySelections(plan))
    }

    /**
     * Builds the [GameServer] for [initial] out of everything merged into [registry] so far, and
     * tells the parked peer (if any) to re-join. The caller must [GameServer.connectLocal] its own
     * local seat(s) immediately afterward — see this class's KDoc on ordering, mirroring
     * [GameServer.connectLocal]'s own "local seat before the acceptor could let a socket client
     * attach" rule — but unlike that rule, "immediately afterward, same thread" is NOT enough to
     * guarantee it here on its own: the parked peer's reader thread is already alive and blocked
     * in a socket read, ready to react to [ServerMessage.LobbyCommitted] the instant it arrives,
     * while [GameServer.connectLocal] must first spin up a fresh thread — measurably slower, and
     * not reliably faster than a live socket peer reacting to bytes that already arrived. So
     * [onServerReady] runs BEFORE [ServerMessage.LobbyCommitted] is sent at all: a caller that
     * calls [GameServer.connectLocal] from inside it is racing nothing, deterministically, rather
     * than merely favored to win a race it might still lose under load.
     */
    public fun commit(initial: GameState, onServerReady: (GameServer) -> Unit = {}): GameServer {
        val (server, peer) = synchronized(lock) {
            check(gameServer == null) { "LobbyHost.commit called more than once" }
            val bundle = AssetBundle(maps = mergedRegistry.maps.values.toList(), mechs = mergedRegistry.mechs.values.toList())
            val built = GameServer.host(initial, bundle, sessionId)
            gameServer = built
            built to parked
        }
        onServerReady(server)
        peer?.send(ServerMessage.LobbyCommitted)
        return server
    }

    override fun close() {
        synchronized(lock) { parked }?.close()
    }

    /** Adapts [attach] to the [ConnectionSink] seam [SocketAcceptor] depends on — see [GameServer.asConnectionSink]'s KDoc for why. */
    internal fun asConnectionSink(): ConnectionSink = object : ConnectionSink {
        override fun attach(connection: ServerConnection, onJoinAccepted: () -> Unit) =
            this@LobbyHost.attach(connection, onJoinAccepted)
    }

    /** What [attach]'s one atomic decision resolved to — acted on outside the lock. */
    private sealed interface Admission {
        /** The match already exists: this connection is the game's, never the lobby's. */
        data class Forward(val server: GameServer) : Admission
        data class Reject(val reason: JoinRejectionReason) : Admission
        data class Park(val registry: AssetRegistry) : Admission
    }

    /**
     * Runs on the acceptor's per-connection thread. Either forwards [connection] straight to an
     * already-committed match, rejects it, or parks it (sending [ServerMessage.LobbyJoined] with
     * the merged catalog) and blocks for whatever the peer sends next:
     * - **connection closes (`null`)**: the peer dropped while parked — un-park it and fire [onChange].
     * - **another [ClientMessage.Join]**: [commit] has happened and the peer re-sent its original
     *   `Join`, exactly as `docs/wire-protocol.md` describes — hand the connection and that second
     *   `Join` to [GameServer.attach]'s two-argument overload, so this thread becomes that seat's
     *   reader thread, exactly as a fresh socket join would set up.
     *
     * The [Admission.Forward] arm is what keeps this sink usable for a match's whole life: an
     * interactive host's [SocketAcceptor] is built over this lobby and is never rebuilt over the
     * [GameServer] that [commit] returns, so every later connection — a seat rejoining after a
     * disconnect included — still arrives here. Parking one of those would strand it waiting for a
     * [ServerMessage.LobbyCommitted] that has already been sent. Resolving admission and the park
     * registration in ONE synchronized block is what makes that safe against a [commit] racing an
     * inbound join: whichever wins the lock, the loser sees the other's result.
     */
    private fun attach(connection: ServerConnection, onJoinAccepted: () -> Unit) {
        val join = connection.receive() as? ClientMessage.Join ?: run {
            connection.close()
            return
        }

        val admission = synchronized(lock) {
            val committed = gameServer
            when {
                // Validation is the committed server's own job on this path — it applies the very
                // same session/protocol/content checks, plus the seat bookkeeping only it has.
                committed != null -> Admission.Forward(committed)
                parked != null -> Admission.Reject(JoinRejectionReason.SEAT_TAKEN)
                !SessionId.matches(join.sessionId, sessionId) -> Admission.Reject(JoinRejectionReason.UNKNOWN_SESSION)
                join.protocolVersion != PROTOCOL_VERSION -> Admission.Reject(JoinRejectionReason.INCOMPATIBLE_PROTOCOL)
                join.content.duplicateId() != null -> Admission.Reject(JoinRejectionReason.INVALID_CONTENT)
                else -> {
                    mergedRegistry = mergedRegistry.merge(join.content).registry
                    parked = connection
                    Admission.Park(mergedRegistry)
                }
            }
        }

        when (admission) {
            is Admission.Forward -> {
                admission.server.attach(connection, join, onJoinAccepted)
                return
            }
            is Admission.Reject -> {
                connection.send(ServerMessage.JoinRejected(admission.reason))
                connection.close()
                return
            }
            is Admission.Park -> {
                notifyChange()
                connection.send(ServerMessage.LobbyJoined(admission.registry.summarize()))
                onJoinAccepted()
            }
        }

        when (val second = connection.receive()) {
            null -> {
                synchronized(lock) { if (parked === connection) parked = null }
                notifyChange()
            }
            is ClientMessage.Join -> {
                // Un-parked BEFORE the hand-off: from here on this connection belongs to the
                // match, and a stale `parked` would answer SEAT_TAKEN to every later join —
                // including this same seat rejoining after a disconnect, which GameServer.attach
                // is built to accept.
                val server = synchronized(lock) {
                    if (parked === connection) parked = null
                    checkNotNull(gameServer) { "LobbyHost: peer re-joined before commit() built the GameServer" }
                }
                server.attach(connection, second, onJoinAccepted = {})
            }
            else -> connection.close()
        }
    }
}
