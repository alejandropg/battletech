package io.archinaut.battletech.network.client

import io.archinaut.battletech.network.transport.ClientConnection
import io.archinaut.battletech.network.wire.ClientMessage
import io.archinaut.battletech.network.wire.GameSnapshot
import io.archinaut.battletech.network.wire.JoinRejectionReason
import io.archinaut.battletech.network.wire.MatchBootstrap
import io.archinaut.battletech.network.wire.PROTOCOL_VERSION
import io.archinaut.battletech.network.wire.ServerMessage
import io.archinaut.battletech.network.wire.SessionId
import io.archinaut.battletech.tactical.model.GameMap
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.model.TurnPhase
import io.archinaut.battletech.tactical.model.content.AssetBundle
import io.archinaut.battletech.tactical.query.DefaultPlayerView
import io.archinaut.battletech.tactical.query.PlayerGameState
import io.archinaut.battletech.tactical.query.PlayerView
import io.archinaut.battletech.tactical.session.CommandRejection
import io.archinaut.battletech.tactical.session.CommandResult
import io.archinaut.battletech.tactical.session.GameCommand
import io.archinaut.battletech.tactical.session.GameEvent
import io.archinaut.battletech.tactical.session.GameLog
import io.archinaut.battletech.tactical.session.GameSession
import io.archinaut.battletech.tactical.session.HostConnectionLost
import io.archinaut.battletech.tactical.session.LogEntry
import io.archinaut.battletech.tactical.session.Subscription
import io.archinaut.battletech.tactical.session.TurnState
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** [ClientGameSession.connect] refused by the host: see [reason]. */
public class JoinRejectedException(public val reason: JoinRejectionReason) : Exception("Join rejected: $reason")

/**
 * Client-side session endpoint: a read-only replica of a host's
 * [io.archinaut.battletech.tactical.session.BattleSession], seated at whichever [playerId]
 * the server assigned at join time, kept fresh by a background reader
 * thread that applies [ServerMessage.StatePush]es as they arrive. The transport
 * underneath may be a real socket ([io.archinaut.battletech.network.transport.JsonLineConnection]) or an in-process
 * [io.archinaut.battletech.network.transport.InMemoryConnection] half (see
 * [io.archinaut.battletech.network.server.GameServer.connectLocal]); this class neither knows nor cares
 * which.
 *
 * **Ordering invariant** (see [ServerMessage] KDoc): for an accepted command
 * the [ServerMessage.StatePush] carrying the change arrives on the wire
 * before the corresponding [ServerMessage.CommandReply]. The single reader
 * thread applies both in that order — swaps [snapshot], appends to [log],
 * then dispatches events — so by the time a blocked [submitCommand] call
 * returns, [turnState]/[currentPhase] already reflect the accepted command.
 * Callers may read post-submit state immediately.
 *
 * [snapshot]`.units` plus [map] together ARE [PlayerGameState] — [playerId]'s own projection,
 * exactly as the host computed and sent it (see [io.archinaut.battletech.network.server.GameServer.snapshotFor]).
 * [map] arrives once, in the [ServerMessage.JoinAccepted] that builds this session, and never
 * changes again (the board is immutable for a match — see [io.archinaut.battletech.network.wire.GameSnapshot]'s
 * KDoc for why it isn't repeated in every [ServerMessage.StatePush]).
 * Host content — the map and every mech model — is authoritative. This class checks nothing of
 * its own against it: the shared registry the host arbitrates
 * ([io.archinaut.battletech.network.server.GameServer]'s `assetRegistry`) is the sole source of any
 * `AssetConflict` finding, and those arrive pre-redacted inside [initial]`.log`/later pushes like
 * any other event.
 *
 * That projection is enough to serve this seat completely, with no round trip: [stateFor],
 * [logFor] and [viewFor] all answer locally for [playerId]. The query engine
 * ([io.archinaut.battletech.tactical.movement.ReachabilityCalculator], [io.archinaut.battletech.tactical.query.WeaponTargeting],
 * [io.archinaut.battletech.tactical.query.PhysicalAttackQueries]) consumes the projection directly,
 * resolving the ACTOR (always a unit this seat owns) through
 * [io.archinaut.battletech.tactical.query.PlayerGameState.ownUnitById] and leaving every other unit
 * [io.archinaut.battletech.tactical.unit.VisibleUnit]-shaped, because every field it reads off a
 * non-actor unit is public.
 *
 * None of the three can honestly answer for a DIFFERENT viewer — there is no raw state left
 * to re-project from — so all three refuse rather than guess; see their KDoc.
 */
public class ClientGameSession internal constructor(
    private val connection: ClientConnection,
    initial: MatchBootstrap,
) : GameSession, AutoCloseable {

    /** The seat the server assigned this connection at join time. */
    public val playerId: PlayerId = initial.playerId

    /**
     * The board, as sent once in [initial] — see the class KDoc for why this never changes
     * again over this session's lifetime.
     */
    private val map: GameMap = initial.map

    @Volatile
    private var snapshot: GameSnapshot = initial.snapshot

    private val log: GameLog = GameLog()
    private val listeners: MutableList<(GameEvent) -> Unit> = mutableListOf()
    private val pendingReply: ArrayBlockingQueue<ServerMessage.CommandReply> = ArrayBlockingQueue(1)
    private val requestIdCounter: AtomicLong = AtomicLong(0)
    private val readerThread: Thread

    @Volatile
    private var connectionLost: Boolean = false

    init {
        initial.log.forEach { log.append(it) }
        readerThread = thread(isDaemon = true, name = "client-session-reader") { readLoop() }
    }

    public override val turnState: TurnState get() = snapshot.turnState
    public override val currentPhase: TurnPhase get() = snapshot.currentPhase
    public override val activePlayer: PlayerId? get() = snapshot.activePlayer
    public override val isMatchOver: Boolean get() = snapshot.isMatchOver
    public override val gameLog: GameLog get() = log

    /**
     * Answers "what is legal right now?" for THIS connection's own seat, locally, with no
     * round trip: [DefaultPlayerView] consumes the same [PlayerGameState] projection the
     * host's [io.archinaut.battletech.tactical.session.BattleSession.viewFor] feeds it.
     *
     * Refuses any OTHER seat, for the same reason as [stateFor]: this replica holds only its
     * own projection, so it has neither the data nor the standing to build the opponent's
     * view.
     */
    public override fun viewFor(playerId: PlayerId): PlayerView {
        require(playerId == this.playerId) {
            "ClientGameSession.viewFor: this replica can only build a view for its own seat " +
                "(${this.playerId}); it holds no projection for $playerId."
        }
        return DefaultPlayerView(playerId, PlayerGameState(snapshot.units, map), snapshot.turnState)
    }

    /**
     * Serves ONLY [playerId] (this connection's own seat): [snapshot]`.units` plus [map]
     * together already ARE that projection, exactly as the host built and sent it, so no
     * re-projection happens here. Any other [viewer] — including `null` — throws rather than
     * guess: this class holds no raw state to re-project from, so returning it unchanged for a
     * different viewer would silently hand back the wrong player's shape under a false label,
     * and returning it for `null` would misrepresent "I don't know who is looking" as "here is
     * player X's view" — both are the "return the wrong thing silently" this design explicitly
     * rules out.
     */
    public override fun stateFor(viewer: PlayerId?): PlayerGameState {
        require(viewer == playerId) {
            "ClientGameSession.stateFor: this replica only holds $playerId's own projection " +
                "(from the host's snapshot); it cannot serve viewer=$viewer without raw state to re-project from."
        }
        return PlayerGameState(snapshot.units, map)
    }

    /**
     * Serves ONLY [playerId], same rule as [stateFor]: [log] already holds the host's
     * [io.archinaut.battletech.tactical.session.GameEvent.redactFor]-filtered entries for THIS seat
     * (see [io.archinaut.battletech.network.server.GameServer.snapshotFor]'s KDoc for the outbound
     * paths that redact it before it ever reaches [readLoop]), so it's returned as-is
     * rather than re-redacted for a viewer this class has no raw state to check against.
     */
    public override fun logFor(viewer: PlayerId?): List<LogEntry> {
        require(viewer == playerId) {
            "ClientGameSession.logFor: this replica only holds $playerId's own redacted log; " +
                "it cannot serve viewer=$viewer without raw state to re-redact against."
        }
        return log.snapshot()
    }

    public override fun subscribe(listener: (GameEvent) -> Unit): Subscription {
        listeners += listener
        return object : Subscription {
            override fun unsubscribe() {
                listeners.remove(listener)
            }
        }
    }

    /**
     * Sends [command] to the host and blocks for the matching
     * [ServerMessage.CommandReply]. See the ordering invariant in the class
     * doc: [snapshot] is already up to date by the time this returns for an
     * accepted command. A lost/timed-out connection is reported as
     * [CommandRejection.OpponentUnavailable] rather than thrown.
     */
    public override fun submitCommand(command: GameCommand): CommandResult {
        if (connectionLost) return CommandResult.Rejected(CommandRejection.OpponentUnavailable)

        val requestId = requestIdCounter.incrementAndGet()
        return try {
            connection.send(ClientMessage.SubmitCommand(requestId, command))
            val reply = pendingReply.poll(REPLY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            when {
                reply == null -> {
                    connectionLost = true
                    CommandResult.Rejected(CommandRejection.OpponentUnavailable)
                }
                reply.requestId != requestId -> {
                    // Protocol violation (stale/mismatched reply) — treat the connection as unusable.
                    connectionLost = true
                    CommandResult.Rejected(CommandRejection.OpponentUnavailable)
                }
                else -> reply.result
            }
        } catch (e: IOException) {
            connectionLost = true
            CommandResult.Rejected(CommandRejection.OpponentUnavailable)
        }
    }

    public override fun close() {
        readerThread.interrupt()
        connection.close()
    }

    private fun readLoop() {
        try {
            while (true) {
                val message = connection.receive() ?: break
                when (message) {
                    is ServerMessage.StatePush -> {
                        snapshot = message.snapshot
                        for (entry in message.entries) {
                            log.append(entry)
                            dispatch(entry.event)
                        }
                    }
                    is ServerMessage.CommandReply -> pendingReply.offer(message)
                    is ServerMessage.JoinAccepted ->
                        throw SerializationException("Host sent more than one match bootstrap")
                    is ServerMessage.JoinRejected ->
                        throw SerializationException("Host sent JoinRejected after accepting the connection")
                    // Lobby messages only ever precede JoinAccepted on this connection — consumed
                    // by LobbyClient's own (temporary) reader before a ClientGameSession exists at
                    // all — so they are unreachable here. See LobbyClient's KDoc.
                    is ServerMessage.LobbyJoined, is ServerMessage.LobbySelections, ServerMessage.LobbyCommitted ->
                        throw SerializationException("Unexpected lobby message after the match started: $message")
                }
            }
        } catch (e: InterruptedException) {
            return // close() requested shutdown; nothing more to report
        } catch (e: SerializationException) {
            runCatching { connection.close() }
        }

        connectionLost = true
        pendingReply.offer(ServerMessage.CommandReply(UNSOLICITED_REQUEST_ID, CommandResult.Rejected(CommandRejection.OpponentUnavailable)))
        log.append(LogEntry(snapshot.turnState.turnNumber, HostConnectionLost))
        dispatch(HostConnectionLost)
    }

    private fun dispatch(event: GameEvent) {
        val snapshotOfListeners = listeners.toList()
        for (listener in snapshotOfListeners) listener(event)
    }

    public companion object {
        private const val REPLY_TIMEOUT_SECONDS: Long = 30
        private const val UNSOLICITED_REQUEST_ID: Long = -1

        /**
         * Opens a socket to [host]:[port], sends [ClientMessage.Join] for [sessionId]
         * contributing [content], and blocks for the match to become available — immediately if
         * already committed, or after the full lobby exchange ([LobbyClient]) otherwise. A caller
         * that cares about the lobby phase (the setup screen's joiner mirror) uses [LobbyClient]
         * directly instead of this shortcut.
         *
         * @throws JoinRejectedException if the host refuses the join.
         */
        public fun connect(
            host: String,
            port: Int,
            sessionId: String,
            content: AssetBundle = AssetBundle.EMPTY,
        ): ClientGameSession = LobbyClient.connect(host, port, sessionId, content).awaitMatch()

        /**
         * Sends [ClientMessage.Join] on [connection] and blocks for the host's handshake
         * response, building the [ClientGameSession] on acceptance. Shared by [connect] (a real
         * socket) and [io.archinaut.battletech.network.server.GameServer.connectLocal] (an in-process
         * [io.archinaut.battletech.network.transport.InMemoryConnection] half). [connection] is closed on
         * any failure path; on success it becomes the returned session's transport.
         *
         * @throws JoinRejectedException if the host refuses the join.
         */
        internal fun handshake(
            connection: ClientConnection,
            sessionId: String,
            content: AssetBundle = AssetBundle.EMPTY,
        ): ClientGameSession {
            try {
                connection.send(ClientMessage.Join(SessionId.normalize(sessionId), PROTOCOL_VERSION, content))

                val response = connection.receive()
                    ?: throw IOException("Connection closed before the host replied to Join")
                return when (response) {
                    is ServerMessage.JoinRejected -> throw JoinRejectedException(response.reason)
                    is ServerMessage.JoinAccepted -> ClientGameSession(connection, response.bootstrap)
                    else -> throw IOException("Unexpected first message from host: $response")
                }
            } catch (failure: Exception) {
                try {
                    connection.close()
                } catch (closeFailure: Exception) {
                    failure.addSuppressed(closeFailure)
                }
                throw failure
            }
        }
    }
}
