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
public class LobbyHost(
    public val sessionId: String = SessionId.generate(),
    ownContent: AssetBundle = AssetBundle.EMPTY,
) : AutoCloseable {

    private val lock: Any = Any()
    private var mergedRegistry: AssetRegistry = AssetRegistry.EMPTY.merge(ownContent).registry
    private var parked: ServerConnection? = null
    private var gameServer: GameServer? = null
    private val listeners: MutableList<(AssetRegistry) -> Unit> = mutableListOf()

    /** The merged registry as it stands right now — read under the lock. */
    public val registry: AssetRegistry get() = synchronized(lock) { mergedRegistry }

    public val opponentConnected: Boolean get() = synchronized(lock) { parked != null }

    /** Fired (with the merged registry) whenever a peer parks or the parked peer drops. */
    public fun onChange(listener: (AssetRegistry) -> Unit) {
        listeners += listener
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
     * attach" rule: here, the parked peer only re-attaches after it reads [ServerMessage.LobbyCommitted],
     * but that happens as soon as this method sends it, so the caller must not delay.
     */
    public fun commit(initial: GameState): GameServer {
        val (server, peer) = synchronized(lock) {
            check(gameServer == null) { "LobbyHost.commit called more than once" }
            val bundle = AssetBundle(maps = mergedRegistry.maps.values.toList(), mechs = mergedRegistry.mechs.values.toList())
            val built = GameServer.host(initial, bundle, sessionId)
            gameServer = built
            built to parked
        }
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

    /**
     * Runs on the acceptor's per-connection thread. Parks [connection] (sending [ServerMessage.LobbyJoined]
     * with the merged catalog) and then blocks for whatever the peer sends next:
     * - **connection closes (`null`)**: the peer dropped while parked — un-park it and fire [onChange].
     * - **another [ClientMessage.Join]**: [commit] has happened and the peer re-sent its original
     *   `Join`, exactly as `docs/wire-protocol.md` describes — hand the connection and that second
     *   `Join` to [GameServer.attach]'s two-argument overload, so this thread becomes that seat's
     *   reader thread, exactly as a fresh socket join would set up.
     */
    private fun attach(connection: ServerConnection, onJoinAccepted: () -> Unit) {
        val join = connection.receive() as? ClientMessage.Join ?: run {
            connection.close()
            return
        }

        val rejection = synchronized(lock) {
            when {
                parked != null -> JoinRejectionReason.SEAT_TAKEN
                !SessionId.matches(join.sessionId, sessionId) -> JoinRejectionReason.UNKNOWN_SESSION
                join.protocolVersion != PROTOCOL_VERSION -> JoinRejectionReason.INCOMPATIBLE_PROTOCOL
                join.content.duplicateId() != null -> JoinRejectionReason.INVALID_CONTENT
                else -> null
            }
        }
        if (rejection != null) {
            connection.send(ServerMessage.JoinRejected(rejection))
            connection.close()
            return
        }

        val parkedRegistry = synchronized(lock) {
            val merged = mergedRegistry.merge(join.content)
            mergedRegistry = merged.registry
            parked = connection
            mergedRegistry
        }
        listeners.forEach { it(parkedRegistry) }

        connection.send(ServerMessage.LobbyJoined(parkedRegistry.summarize()))
        onJoinAccepted()

        when (val second = connection.receive()) {
            null -> {
                synchronized(lock) { if (parked === connection) parked = null }
                listeners.forEach { it(registry) }
            }
            is ClientMessage.Join -> {
                val server = checkNotNull(synchronized(lock) { gameServer }) {
                    "LobbyHost: peer re-joined before commit() built the GameServer"
                }
                server.attach(connection, second, onJoinAccepted = {})
            }
            else -> connection.close()
        }
    }
}
