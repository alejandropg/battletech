package battletech.network.server

import battletech.network.wire.ClientMessage
import battletech.network.wire.GameSnapshot
import battletech.network.wire.JoinRejectionReason
import battletech.network.wire.PROTOCOL_VERSION
import battletech.network.wire.ServerMessage
import battletech.network.wire.SessionId
import battletech.network.wire.WireJson
import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.PlayerGameState
import battletech.tactical.query.PlayerView
import battletech.tactical.session.BattleSession
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommandResult
import battletech.tactical.session.GameCommand
import battletech.tactical.session.GameEvent
import battletech.tactical.session.GameLog
import battletech.tactical.session.GameSession
import battletech.tactical.session.LogEntry
import battletech.tactical.session.SessionNotice
import battletech.tactical.session.Subscription
import battletech.tactical.session.TurnState
import battletech.tactical.session.redactFor
import battletech.tactical.unit.UnknownUnitException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Host-side network endpoint: wraps an authoritative [BattleSession] and
 * exposes it to a configurable set of remote seats ([remoteSeats]) over
 * newline-delimited JSON on a TCP socket.
 *
 * Two configurations share this class:
 * - **Host-embedded** (the default: `remoteSeats = {PLAYER_2}`): the host's
 *   own UI drives the wrapped session through [submitCommand] as
 *   [PlayerId.PLAYER_1], and exactly one remote client connects and plays
 *   [PlayerId.PLAYER_2]. Every touch of [session] — host UI thread,
 *   connected client's reader thread, or the join-time
 *   [BattleSession.advance] kickstart — goes through [lock].
 * - **Headless** (`remoteSeats = {PLAYER_1, PLAYER_2}`): nobody drives the
 *   host [GameSession] surface directly; both players connect remotely and
 *   [submitCommand] simply stays frozen (no local seat to serve).
 *
 * Seat enforcement is symmetric: [submitCommand] (the host UI path) rejects
 * with [CommandRejection.NotYourTurn] unless `command.playerId` is
 * [PlayerId.PLAYER_1], and the reader loop in [runReaderLoop] applies the
 * mirror-image check for whichever seat was assigned to that connection at
 * handshake time — neither side can act as another seat's, active-turn or
 * not.
 *
 * [snapshotFor] is the one seam every outbound [GameSnapshot] (join
 * acceptance and pushes alike) is built through — see its KDoc for how
 * redaction happens there, and why the log traveling alongside it
 * ([ServerMessage.JoinAccepted.log], [ServerMessage.StatePush.entries]) must
 * redact in lockstep.
 *
 * Disconnects freeze the whole session: both [submitCommand] and the remote
 * reader loop reject with [CommandRejection.OpponentUnavailable] whenever
 * any seat in [remoteSeats] is vacant. The accept loop keeps listening, so a
 * rejoin (same session id) is just another successful handshake — it
 * resends the full log but never re-runs the join-time kickstart, which
 * fires at most once per server lifetime (the moment every remote seat has
 * connected for the first time).
 */
public class GameServer(
    private val session: BattleSession,
    private val sessionId: String,
    port: Int,
    private val remoteSeats: Set<PlayerId> = setOf(PlayerId.PLAYER_2),
) : GameSession, AutoCloseable {

    init {
        require(remoteSeats.isNotEmpty()) { "remoteSeats must not be empty" }
    }

    private val lock: Any = Any()
    private val serverSocket: ServerSocket = ServerSocket(port)

    @Volatile
    private var running: Boolean = true

    private val clients: MutableMap<PlayerId, ConnectedClient> = mutableMapOf()
    private var everStarted: Boolean = false

    /** The actual bound port — meaningful even when constructed with port 0. */
    public val boundPort: Int get() = serverSocket.localPort

    /** Starts the accept loop on a daemon thread. Safe to call once. */
    public fun start() {
        thread(isDaemon = true, name = "game-server-accept") {
            while (running) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: IOException) {
                    null
                }
                when {
                    socket != null -> handleClientSocket(socket)
                    !running -> return@thread
                }
            }
        }
    }

    // ---------- GameSession overrides (host UI path) ----------

    /**
     * The authoritative, unredacted state — NOT part of [GameSession] (a remote client has
     * no equivalent). Legitimate here because [GameServer] runs in-process with [session]:
     * this never crosses the wire itself, only the per-seat projections built from it do
     * (see [snapshotFor]). Used by this class's own outbound-message building and by tests
     * that need to inspect/drive the authoritative state directly.
     */
    public val gameState: GameState get() = synchronized(lock) { session.gameState }
    public override val turnState: TurnState get() = synchronized(lock) { session.turnState }
    public override val currentPhase: TurnPhase get() = synchronized(lock) { session.currentPhase }
    public override val activePlayer: PlayerId? get() = synchronized(lock) { session.activePlayer }
    public override val isMatchOver: Boolean get() = synchronized(lock) { session.isMatchOver }
    public override val gameLog: GameLog get() = synchronized(lock) { session.gameLog }

    public override fun viewFor(playerId: PlayerId): PlayerView = synchronized(lock) { session.viewFor(playerId) }

    public override fun stateFor(viewer: PlayerId?): PlayerGameState = synchronized(lock) { session.stateFor(viewer) }

    public override fun logFor(viewer: PlayerId?): List<LogEntry> = synchronized(lock) { session.logFor(viewer) }

    /**
     * Listener bodies must be thread-safe on the caller's side: dispatch may
     * occur on a client's reader thread (in response to a remote command) as
     * well as on whichever thread calls [submitCommand].
     */
    public override fun subscribe(listener: (GameEvent) -> Unit): Subscription =
        session.subscribe(listener)

    public override fun submitCommand(command: GameCommand): CommandResult = synchronized(lock) {
        if (!clients.keys.containsAll(remoteSeats)) {
            CommandResult.Rejected(CommandRejection.OpponentUnavailable)
        } else if (command.playerId != PlayerId.PLAYER_1) {
            CommandResult.Rejected(
                CommandRejection.NotYourTurn(activePlayer = PlayerId.PLAYER_1, attemptedBy = command.playerId),
            )
        } else {
            submitAndPush(command)
        }
    }

    public override fun close() {
        running = false
        serverSocket.close()
        val connected = synchronized(lock) { clients.values.toList() }
        connected.forEach { disconnect(it) }
    }

    // ---------- shared submit path ----------

    /**
     * Applies [command] to [session] and, if accepted, enqueues the
     * resulting log delta + a fresh per-seat snapshot to EVERY connected
     * client — must be called under [lock]. Each client gets its OWN
     * redaction: [snapshotFor] projects state for that client's seat, and
     * [redactedDeltaFor] filters the same log delta the same way, so the two
     * halves of the push never disagree about what that seat may see.
     * Callers enqueue their own [ServerMessage.CommandReply] afterwards; the
     * push always precedes the reply (the wire ordering invariant documented
     * on [ServerMessage]).
     */
    private fun submitAndPush(command: GameCommand): CommandResult {
        val mark = session.gameLog.snapshot().size
        val result = session.submitCommand(command)
        if (result is CommandResult.Accepted) {
            val delta = session.gameLog.snapshot().drop(mark)
            clients.values.forEach { client ->
                client.outbound.put(ServerMessage.StatePush(redactedDeltaFor(delta, client.seat), snapshotFor(client.seat)))
            }
        }
        return result
    }

    /**
     * The one place a real trust boundary exists in this codebase: server ->
     * wire -> client. Everything on the [session] side is in-process, same
     * JVM, no adversary; this is where bytes leave for a socket a remote
     * client controls — and, per this stage, a client that may be modified
     * or actively cheating.
     *
     * [seat] gets [session]'s state PROJECTED for it, via [GameSession.stateFor]
     * — the same seam [BattleSession.stateFor] uses for the in-process TUI, so a
     * connected seat can see no more of another seat's units than a hot-seat
     * viewer could. This is only ONE of three outbound paths that must agree:
     * [ServerMessage.JoinAccepted] carries [GameSession.logFor] (not the raw
     * [session]`.gameLog.snapshot()`), and every [ServerMessage.StatePush]
     * carries a log delta run through [redactedDeltaFor] — see [attachTransport]
     * and [submitAndPush]. Redacting only this snapshot and leaving either log
     * channel un-redacted leaks everything through that other channel; that
     * mismatch is exactly why the previous (deleted) redaction attempt never
     * worked. No sentinel/fake values are used anywhere in this path — a
     * [PlayerGameState] simply doesn't have a field to leak for units [seat]
     * doesn't own ([battletech.tactical.query.ForeignUnit] has no
     * gunnery/heat/internals field at all).
     */
    private fun snapshotFor(seat: PlayerId): GameSnapshot = GameSnapshot(
        gameState = session.stateFor(seat),
        turnState = session.turnState,
        currentPhase = session.currentPhase,
        activePlayer = session.activePlayer,
        isMatchOver = session.isMatchOver,
    )

    /**
     * Redacts a game-log SLICE (a [ServerMessage.StatePush] delta, rather than the whole
     * log [GameSession.logFor] redacts) for [seat], entry by entry via
     * [battletech.tactical.session.redactFor] — same rule, same [session]`.gameState` used
     * for ownership lookups, same [GameSession.isMatchOver] reveal, just scoped to the
     * entries that changed since the last push instead of the full history.
     */
    private fun redactedDeltaFor(entries: List<LogEntry>, seat: PlayerId): List<LogEntry> =
        entries.mapNotNull { entry ->
            entry.event.redactFor(seat, session.gameState, revealAll = session.isMatchOver)?.let { entry.copy(event = it) }
        }

    // ---------- transport ----------

    private fun handleClientSocket(socket: Socket) {
        thread(isDaemon = true, name = "game-server-client") {
            try {
                socket.soTimeout = HANDSHAKE_TIMEOUT_MS
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = OutputStreamWriter(socket.getOutputStream())
                attachTransport(
                    input = input,
                    output = output,
                    onJoinAccepted = { socket.soTimeout = 0 },
                    onDisconnect = { socket.close() },
                )
            } catch (e: IOException) {
                socket.close()
            }
        }
    }

    /**
     * Performs the join handshake on [input]/[output] and, on success, runs
     * the per-client reader loop inline (blocking the calling thread until
     * disconnect). Shared by the real-socket accept path (each connection
     * gets its own daemon thread) and pipe-based tests (the test spawns the
     * thread itself). [onJoinAccepted] fires once, right after a successful
     * handshake — the real path uses it to clear the handshake [soTimeout];
     * [onDisconnect] fires when the transport should be torn down (socket
     * close for real connections, stream close for pipes).
     */
    internal fun attachTransport(
        input: BufferedReader,
        output: Writer,
        onJoinAccepted: () -> Unit = {},
        onDisconnect: () -> Unit = {},
    ) {
        val line = input.readLine() ?: return
        val join = WireJson.decodeClientMessage(line) as? ClientMessage.Join ?: return

        val rejection = synchronized(lock) {
            when {
                clients.keys.containsAll(remoteSeats) -> JoinRejectionReason.SEAT_TAKEN
                !SessionId.matches(join.sessionId, sessionId) -> JoinRejectionReason.UNKNOWN_SESSION
                join.protocolVersion != PROTOCOL_VERSION -> JoinRejectionReason.INCOMPATIBLE_PROTOCOL
                else -> null
            }
        }

        if (rejection != null) {
            output.write(WireJson.encodeToLine(ServerMessage.JoinRejected(rejection)) + "\n")
            output.flush()
            onDisconnect()
            return
        }

        onJoinAccepted()

        val connected = synchronized(lock) {
            val seat = (remoteSeats - clients.keys).min()
            val client = ConnectedClient(seat = seat, output = output, onDisconnect = onDisconnect)
            val alreadyConnected = clients.values.toList()

            // Everything from here up to (and including) the connect notice is new to
            // alreadyConnected clients, but the joiner gets it all for free via JoinAccepted.log below.
            val markBeforeConnectNotice = session.gameLog.snapshot().size
            session.annotate(SessionNotice("${seatLabel(seat)} connected"))
            clients[seat] = client
            startWriterThread(client)
            val markBeforeKickstart = session.gameLog.snapshot().size
            // logFor(seat), not gameLog.snapshot() — the joiner's very first message must
            // already be redacted, same as every later push (see snapshotFor's KDoc).
            client.outbound.put(ServerMessage.JoinAccepted(seat, snapshotFor(seat), session.logFor(seat)))

            val kickstarted = !everStarted && clients.keys == remoteSeats
            if (kickstarted) {
                everStarted = true
                session.advance()
            }

            // Already-connected clients missed the connect notice (and the kickstart, if it just
            // fired) entirely — push them the combined delta, redacted for THEIR OWN seat (not the
            // joiner's). The joiner already has the connect notice via JoinAccepted.log above, so
            // it only needs the kickstart delta, if any.
            val finalLog = session.gameLog.snapshot()
            alreadyConnected.forEach { c ->
                c.outbound.put(ServerMessage.StatePush(redactedDeltaFor(finalLog.drop(markBeforeConnectNotice), c.seat), snapshotFor(c.seat)))
            }
            if (kickstarted) {
                client.outbound.put(ServerMessage.StatePush(redactedDeltaFor(finalLog.drop(markBeforeKickstart), seat), snapshotFor(seat)))
            }
            client
        }

        runReaderLoop(input, connected)
    }

    private fun runReaderLoop(input: BufferedReader, connected: ConnectedClient) {
        try {
            while (true) {
                val line = input.readLine() ?: break
                val message = WireJson.decodeClientMessage(line) as? ClientMessage.SubmitCommand ?: continue
                synchronized(lock) {
                    val result = when {
                        !clients.keys.containsAll(remoteSeats) ->
                            CommandResult.Rejected(CommandRejection.OpponentUnavailable)
                        message.command.playerId != connected.seat ->
                            CommandResult.Rejected(
                                CommandRejection.NotYourTurn(
                                    activePlayer = connected.seat,
                                    attemptedBy = message.command.playerId,
                                ),
                            )
                        else -> try {
                            submitAndPush(message.command)
                        } catch (e: UnknownUnitException) {
                            // A correctly-behaving client can never name a UnitId that
                            // doesn't exist (see UnknownUnitException's KDoc) — this is a
                            // malformed/tampered command, not a gameplay rejection. Reply
                            // and keep the connection alive rather than let the exception
                            // unwind through synchronized(lock) and kill this reader thread.
                            CommandResult.ProtocolError(e.message ?: "unknown unit")
                        }
                    }
                    connected.outbound.put(ServerMessage.CommandReply(message.requestId, result))
                }
            }
        } catch (e: IOException) {
            // fall through to disconnect
        } finally {
            disconnect(connected)
        }
    }

    private fun startWriterThread(connected: ConnectedClient) {
        connected.writerThread = thread(isDaemon = true, name = "game-server-write") {
            try {
                while (true) {
                    val message = connected.outbound.take()
                    connected.output.write(WireJson.encodeToLine(message) + "\n")
                    connected.output.flush()
                }
            } catch (e: InterruptedException) {
                // shutting down
            } catch (e: IOException) {
                disconnect(connected)
            }
        }
    }

    /**
     * Idempotent: a no-op unless [connected] is still the client currently
     * occupying its seat — identity-checked (not just seat-checked) so a
     * stale disconnect from a superseded connection object can never evict a
     * newer connection that has since reclaimed the same seat.
     */
    private fun disconnect(connected: ConnectedClient) {
        synchronized(lock) {
            if (clients[connected.seat] !== connected) return
            clients.remove(connected.seat)
            connected.writerThread?.interrupt()
            try {
                connected.onDisconnect()
            } catch (e: IOException) {
                // already gone
            }
            session.annotate(SessionNotice("${seatLabel(connected.seat)} disconnected — waiting for rejoin…"))
        }
    }

    private fun seatLabel(seat: PlayerId): String = "Player ${seat.ordinal + 1}"

    private class ConnectedClient(
        internal val seat: PlayerId,
        internal val output: Writer,
        internal val onDisconnect: () -> Unit,
    ) {
        internal val outbound: LinkedBlockingQueue<ServerMessage> = LinkedBlockingQueue()
        internal var writerThread: Thread? = null
    }

    private companion object {
        private const val HANDSHAKE_TIMEOUT_MS = 5000
    }
}
