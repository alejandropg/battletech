package battletech.network.client

import battletech.network.transport.ClientConnection
import battletech.network.transport.JsonLineConnection
import battletech.network.wire.ClientMessage
import battletech.network.wire.MatchBootstrap
import battletech.network.wire.PROTOCOL_VERSION
import battletech.network.wire.ServerMessage
import battletech.network.wire.SessionId
import battletech.tactical.model.content.AssetBundle
import battletech.tactical.model.content.ContentSummary
import battletech.tactical.model.content.MatchPlan
import battletech.tactical.model.content.summarize
import kotlinx.serialization.SerializationException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.concurrent.thread

/**
 * The joiner's half of the setup lobby: performs the [ClientMessage.Join] handshake and, when the
 * match is not yet committed, mirrors the host's setup screen — [catalog], [onSelections] — until
 * [ServerMessage.LobbyCommitted] arrives, at which point it re-sends the SAME [ClientMessage.Join]
 * and [awaitMatch] hands back a live [ClientGameSession].
 *
 * When the match is ALREADY committed at [connect] time (a `join` against a `host`/`server` that
 * started before this call, or a rejoin), [awaitMatch] returns immediately with no lobby phase at
 * all — [ClientGameSession.connect] is exactly `LobbyClient.connect(...).awaitMatch()`, so callers
 * that don't care about the lobby (every caller before this class existed) see no behavior change.
 */
public class LobbyClient private constructor(
    private val connection: ClientConnection,
    private val originalJoin: ClientMessage.Join,
    public val catalog: ContentSummary,
    private val immediateBootstrap: MatchBootstrap?,
) : AutoCloseable {

    /**
     * Guards the listener lists AND the lobby state they replay from ([lastSelections],
     * [committedSeen]). Both are written by [readerThread] and read by whichever thread registers
     * a listener, so neither may be a bare field.
     */
    private val listenerLock: Any = Any()
    private val selectionsListeners: MutableList<(MatchPlan) -> Unit> = mutableListOf()
    private val committedListeners: MutableList<() -> Unit> = mutableListOf()
    private var lastSelections: MatchPlan? = null
    private var committedSeen: Boolean = false
    private val matchReady: CompletableFuture<MatchBootstrap> = CompletableFuture()

    /** True when [connect] found the match already committed — [awaitMatch] returns at once and there is no lobby phase to render. */
    public val isCommitted: Boolean get() = immediateBootstrap != null

    // Only started when parked (immediateBootstrap == null) — an already-committed join has
    // nothing left for this class to read before ClientGameSession starts its own reader.
    private val readerThread: Thread? =
        if (immediateBootstrap == null) thread(isDaemon = true, name = "lobby-client-reader") { readLoop() } else null

    /**
     * Registers [listener] and immediately replays the latest plan already received, if any.
     *
     * The replay is not a convenience: [readerThread] starts the moment this object exists, while
     * a caller cannot register until [connect] has returned — and on the interactive path a whole
     * terminal, theme and renderer are built in between. Without the replay, everything the host
     * published in that window is dropped.
     */
    public fun onSelections(listener: (MatchPlan) -> Unit) {
        val replay = synchronized(listenerLock) {
            selectionsListeners += listener
            lastSelections
        }
        replay?.let(listener)
    }

    /**
     * Registers [listener] and fires it immediately if the host has ALREADY committed — see
     * [onSelections] for the registration window this closes. Dropping this particular event is
     * not merely lossy: the mirror screen would never learn the match had started, and would sit
     * there forever while the host's match waits, frozen, for the seat.
     */
    public fun onCommitted(listener: () -> Unit) {
        val replay = synchronized(listenerLock) {
            committedListeners += listener
            committedSeen
        }
        if (replay) listener()
    }

    /**
     * Blocks until the host commits and the seat is accepted. [readerThread] is guaranteed to
     * have exited by the time this returns (see its KDoc) — required, since [ClientGameSession]
     * starts its OWN reader thread on the same [connection]; two readers on one connection is the
     * one fatal bug this hand-off must avoid.
     */
    public fun awaitMatch(): ClientGameSession {
        val bootstrap = immediateBootstrap ?: try {
            matchReady.get()
        } catch (e: ExecutionException) {
            throw (e.cause as? IOException) ?: IOException(e.cause)
        }
        readerThread?.join()
        return ClientGameSession(connection, bootstrap)
    }

    public override fun close() {
        connection.close()
    }

    /**
     * Dispatches [ServerMessage.LobbySelections] to [selectionsListeners] as they arrive, and on
     * [ServerMessage.LobbyCommitted] re-sends [originalJoin], waits for the resulting
     * [ServerMessage.JoinAccepted], completes [matchReady], and returns — this thread's whole job
     * is done at that point, and it must be gone before [awaitMatch] hands [connection] to a new
     * [ClientGameSession], which starts reading it on a thread of its own.
     */
    private fun readLoop() {
        while (true) {
            val message = connection.receive() ?: run {
                matchReady.completeExceptionally(IOException("Host connection lost while parked in the lobby"))
                return
            }
            when (message) {
                is ServerMessage.LobbySelections -> {
                    val listeners = synchronized(listenerLock) {
                        lastSelections = message.plan
                        selectionsListeners.toList()
                    }
                    listeners.forEach { it(message.plan) }
                }
                ServerMessage.LobbyCommitted -> {
                    val listeners = synchronized(listenerLock) {
                        committedSeen = true
                        committedListeners.toList()
                    }
                    listeners.forEach { it() }
                    try {
                        connection.send(originalJoin)
                        val response = connection.receive()
                            ?: throw IOException("Connection closed before the host re-accepted the join")
                        val accepted = response as? ServerMessage.JoinAccepted
                            ?: throw IOException("Unexpected message after LobbyCommitted: $response")
                        matchReady.complete(accepted.bootstrap)
                    } catch (e: IOException) {
                        matchReady.completeExceptionally(e)
                    }
                    return
                }
                else -> {
                    matchReady.completeExceptionally(SerializationException("Unexpected lobby message: $message"))
                    return
                }
            }
        }
    }

    public companion object {
        /**
         * Opens a socket to [host]:[port] and performs the [ClientMessage.Join] handshake for
         * [sessionId], contributing [content]. Branches on the host's first reply: rejected,
         * already committed ([ServerMessage.JoinAccepted] — [awaitMatch] returns at once), or
         * parked ([ServerMessage.LobbyJoined] — [awaitMatch] blocks for the lobby to finish).
         *
         * @throws JoinRejectedException if the host refuses the join.
         */
        public fun connect(
            host: String,
            port: Int,
            sessionId: String,
            content: AssetBundle = AssetBundle.EMPTY,
        ): LobbyClient {
            val socket = Socket(host, port)
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = OutputStreamWriter(socket.getOutputStream())
            val connection = JsonLineConnection.Client(input, output)
            val join = ClientMessage.Join(SessionId.normalize(sessionId), PROTOCOL_VERSION, content)

            try {
                connection.send(join)
                val response = connection.receive()
                    ?: throw IOException("Connection closed before the host replied to Join")
                return when (response) {
                    is ServerMessage.JoinRejected -> throw JoinRejectedException(response.reason)
                    is ServerMessage.JoinAccepted ->
                        LobbyClient(connection, join, response.bootstrap.registry.summarize(), response.bootstrap)
                    is ServerMessage.LobbyJoined -> LobbyClient(connection, join, response.catalog, null)
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
