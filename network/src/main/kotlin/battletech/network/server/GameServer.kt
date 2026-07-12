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
import battletech.tactical.query.PlayerView
import battletech.tactical.session.BattleSession
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommandResult
import battletech.tactical.session.GameCommand
import battletech.tactical.session.GameEvent
import battletech.tactical.session.GameLog
import battletech.tactical.session.GameSession
import battletech.tactical.session.SessionNotice
import battletech.tactical.session.Subscription
import battletech.tactical.session.TurnState
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
 * exposes it to a single remote client (always seated [PlayerId.PLAYER_2])
 * over newline-delimited JSON on a TCP socket.
 *
 * Also implements [GameSession] itself, so the **host's own UI drives the
 * wrapped session through this class** — that is how host-side command
 * serialisation happens: every touch of [session], whether from the host UI
 * thread, the connected client's reader thread, or the join-time
 * [BattleSession.advance] kickstart, goes through [lock].
 *
 * Seat enforcement is symmetric: [submitCommand] (the host UI path) rejects
 * with [CommandRejection.NotYourTurn] unless `command.playerId` is
 * [PlayerId.PLAYER_1], and the reader loop in [runReaderLoop] applies the
 * mirror-image check for [PlayerId.PLAYER_2] on whatever the connected
 * client sends — neither side can act as the other's seat, active-turn or
 * not.
 *
 * [snapshotFor] is the single seam every outbound [GameSnapshot] (join
 * acceptance and pushes alike) is built through; future hidden-info
 * redaction lands there without touching call sites.
 *
 * Disconnects freeze the host: [submitCommand] rejects with
 * [CommandRejection.OpponentUnavailable] while no client is connected. The
 * accept loop keeps listening, so a rejoin (same session id) is just another
 * successful handshake — it resends the full log but never re-runs the
 * join-time kickstart, which fires at most once per server lifetime.
 */
public class GameServer(
    private val session: BattleSession,
    private val sessionId: String,
    port: Int,
) : GameSession, AutoCloseable {

    private val lock: Any = Any()
    private val serverSocket: ServerSocket = ServerSocket(port)

    @Volatile
    private var running: Boolean = true

    private var client: ConnectedClient? = null
    private var everJoined: Boolean = false

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

    public override val gameState: GameState get() = synchronized(lock) { session.gameState }
    public override val turnState: TurnState get() = synchronized(lock) { session.turnState }
    public override val currentPhase: TurnPhase get() = synchronized(lock) { session.currentPhase }
    public override val activePlayer: PlayerId? get() = synchronized(lock) { session.activePlayer }
    public override val isMatchOver: Boolean get() = synchronized(lock) { session.isMatchOver }
    public override val gameLog: GameLog get() = synchronized(lock) { session.gameLog }

    public override fun viewFor(playerId: PlayerId): PlayerView = synchronized(lock) { session.viewFor(playerId) }

    /**
     * Listener bodies must be thread-safe on the caller's side: dispatch may
     * occur on the client's reader thread (in response to a remote command)
     * as well as on whichever thread calls [submitCommand].
     */
    public override fun subscribe(playerId: PlayerId, listener: (GameEvent) -> Unit): Subscription =
        session.subscribe(playerId, listener)

    public override fun submitCommand(command: GameCommand): CommandResult = synchronized(lock) {
        if (client == null) {
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
        disconnect()
    }

    // ---------- shared submit path ----------

    /**
     * Applies [command] to [session] and, if accepted, enqueues the
     * resulting log delta + fresh snapshot to the connected client — must be
     * called under [lock] with a non-null [client]. Callers enqueue their own
     * [ServerMessage.CommandReply] afterwards; the push always precedes the
     * reply (the wire ordering invariant documented on [ServerMessage]).
     */
    private fun submitAndPush(command: GameCommand): CommandResult {
        val current = checkNotNull(client) { "submitAndPush requires a connected client" }
        val mark = session.gameLog.snapshot().size
        val result = session.submitCommand(command)
        if (result is CommandResult.Accepted) {
            val push = ServerMessage.StatePush(session.gameLog.snapshot().drop(mark), snapshotFor(PlayerId.PLAYER_2))
            current.outbound.put(push)
        }
        return result
    }

    /**
     * The single redaction seam: every [ServerMessage.JoinAccepted] and
     * [ServerMessage.StatePush] is built through here. Currently permissive
     * (mirrors [battletech.tactical.session.EventVisibility]); hidden-info
     * rules land here without touching call sites.
     */
    private fun snapshotFor(player: PlayerId): GameSnapshot = GameSnapshot(
        gameState = session.gameState,
        turnState = session.turnState,
        currentPhase = session.currentPhase,
        activePlayer = session.activePlayer,
        isMatchOver = session.isMatchOver,
    )

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
                client != null -> JoinRejectionReason.SEAT_TAKEN
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

        val connected = ConnectedClient(output = output, onDisconnect = onDisconnect)
        synchronized(lock) {
            session.annotate(SessionNotice("Opponent connected"))
            client = connected
            startWriterThread(connected)
            connected.outbound.put(ServerMessage.JoinAccepted(PlayerId.PLAYER_2, snapshotFor(PlayerId.PLAYER_2), session.gameLog.snapshot()))
            if (!everJoined) {
                everJoined = true
                val mark = session.gameLog.snapshot().size
                session.advance()
                connected.outbound.put(ServerMessage.StatePush(session.gameLog.snapshot().drop(mark), snapshotFor(PlayerId.PLAYER_2)))
            }
        }

        runReaderLoop(input, connected)
    }

    private fun runReaderLoop(input: BufferedReader, connected: ConnectedClient) {
        try {
            while (true) {
                val line = input.readLine() ?: break
                val message = WireJson.decodeClientMessage(line) as? ClientMessage.SubmitCommand ?: continue
                synchronized(lock) {
                    val result = if (message.command.playerId != PlayerId.PLAYER_2) {
                        CommandResult.Rejected(
                            CommandRejection.NotYourTurn(activePlayer = PlayerId.PLAYER_2, attemptedBy = message.command.playerId),
                        )
                    } else {
                        submitAndPush(message.command)
                    }
                    connected.outbound.put(ServerMessage.CommandReply(message.requestId, result))
                }
            }
        } catch (e: IOException) {
            // fall through to disconnect
        } finally {
            disconnect()
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
                disconnect()
            }
        }
    }

    /** Idempotent: a no-op if no client is currently connected. */
    private fun disconnect() {
        synchronized(lock) {
            val current = client ?: return
            client = null
            current.writerThread?.interrupt()
            try {
                current.onDisconnect()
            } catch (e: IOException) {
                // already gone
            }
            session.annotate(SessionNotice("Opponent disconnected — waiting for rejoin…"))
        }
    }

    private class ConnectedClient(
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
