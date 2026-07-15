package battletech.network.client

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
import battletech.tactical.query.DefaultPlayerView
import battletech.tactical.query.PlayerView
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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** [RemoteGameSession.connect] refused by the host: see [reason]. */
public class JoinRejectedException(public val reason: JoinRejectionReason) : Exception("Join rejected: $reason")

/**
 * Client-side network endpoint: a read-only replica of a host's
 * [battletech.tactical.session.BattleSession], seated at whichever [playerId]
 * the server assigned at join time, kept fresh by a background reader
 * thread that applies [ServerMessage.StatePush]es as they arrive.
 *
 * **Ordering invariant** (see [ServerMessage] KDoc): for an accepted command
 * the [ServerMessage.StatePush] carrying the change arrives on the wire
 * before the corresponding [ServerMessage.CommandReply]. The single reader
 * thread applies both in that order — swaps [snapshot], appends to [log],
 * then dispatches events — so by the time a blocked [submitCommand] call
 * returns, [gameState]/[turnState]/[currentPhase] already reflect the
 * accepted command. Callers may read post-submit state immediately.
 *
 * Queries ([viewFor]) run locally against the last-received [snapshot] —
 * no round trip to the host.
 */
public class RemoteGameSession internal constructor(
    private val input: BufferedReader,
    private val output: Writer,
    initial: ServerMessage.JoinAccepted,
    private val onClose: () -> Unit = {},
) : GameSession, AutoCloseable {

    /** The seat the server assigned this connection at join time. */
    public val playerId: PlayerId = initial.playerId

    @Volatile
    private var snapshot: GameSnapshot = initial.snapshot

    private val log: GameLog = GameLog()
    private val listeners: MutableMap<PlayerId, MutableList<(GameEvent) -> Unit>> = mutableMapOf()
    private val pendingReply: ArrayBlockingQueue<ServerMessage.CommandReply> = ArrayBlockingQueue(1)
    private val requestIdCounter: AtomicLong = AtomicLong(0)
    private val readerThread: Thread

    @Volatile
    private var connectionLost: Boolean = false

    init {
        initial.log.forEach { log.append(it) }
        readerThread = thread(isDaemon = true, name = "remote-session-reader") { readLoop() }
    }

    public override val gameState: GameState get() = snapshot.gameState
    public override val turnState: TurnState get() = snapshot.turnState
    public override val currentPhase: TurnPhase get() = snapshot.currentPhase
    public override val activePlayer: PlayerId? get() = snapshot.activePlayer
    public override val isMatchOver: Boolean get() = snapshot.isMatchOver
    public override val gameLog: GameLog get() = log

    public override fun viewFor(playerId: PlayerId): PlayerView =
        DefaultPlayerView(playerId, snapshot.gameState, snapshot.turnState)

    public override fun subscribe(playerId: PlayerId, listener: (GameEvent) -> Unit): Subscription {
        val perPlayer = listeners.getOrPut(playerId) { mutableListOf() }
        perPlayer += listener
        return object : Subscription {
            override fun unsubscribe() {
                perPlayer.remove(listener)
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
            output.write(WireJson.encodeToLine(ClientMessage.SubmitCommand(requestId, command)) + "\n")
            output.flush()
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
        onClose()
    }

    private fun readLoop() {
        try {
            while (true) {
                val line = input.readLine() ?: break
                when (val message = WireJson.decodeServerMessage(line)) {
                    is ServerMessage.StatePush -> {
                        snapshot = message.snapshot
                        for (entry in message.entries) {
                            log.append(entry)
                            dispatch(entry.event)
                        }
                    }
                    is ServerMessage.CommandReply -> pendingReply.offer(message)
                    is ServerMessage.JoinAccepted, is ServerMessage.JoinRejected -> Unit // handshake-only, unexpected here
                }
            }
        } catch (e: IOException) {
            // fall through to connection-lost handling below
        } catch (e: InterruptedException) {
            return // close() requested shutdown; nothing more to report
        }

        connectionLost = true
        pendingReply.offer(ServerMessage.CommandReply(UNSOLICITED_REQUEST_ID, CommandResult.Rejected(CommandRejection.OpponentUnavailable)))
        val notice = SessionNotice("Disconnected from host — restart with --join <host> --session <id> to rejoin")
        log.append(LogEntry(snapshot.turnState.turnNumber, notice))
        dispatch(notice)
    }

    private fun dispatch(event: GameEvent) {
        val snapshotOfListeners = listeners.mapValues { (_, perPlayer) -> perPlayer.toList() }
        for ((_, perPlayer) in snapshotOfListeners) {
            for (listener in perPlayer) listener(event)
        }
    }

    public companion object {
        private const val REPLY_TIMEOUT_SECONDS: Long = 30
        private const val UNSOLICITED_REQUEST_ID: Long = -1

        /**
         * Opens a socket to [host]:[port], sends [ClientMessage.Join] for
         * [sessionId], and blocks for the host's handshake response.
         *
         * @throws JoinRejectedException if the host refuses the join.
         */
        public fun connect(host: String, port: Int, sessionId: String): RemoteGameSession {
            val socket = Socket(host, port)
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = OutputStreamWriter(socket.getOutputStream())

            output.write(WireJson.encodeToLine(ClientMessage.Join(SessionId.normalize(sessionId), PROTOCOL_VERSION)) + "\n")
            output.flush()

            val line = input.readLine() ?: run {
                socket.close()
                throw IOException("Connection closed before the host replied to Join")
            }
            return when (val message = WireJson.decodeServerMessage(line)) {
                is ServerMessage.JoinAccepted -> RemoteGameSession(input, output, message, onClose = { socket.close() })
                is ServerMessage.JoinRejected -> {
                    socket.close()
                    throw JoinRejectedException(message.reason)
                }
                else -> {
                    socket.close()
                    throw IOException("Unexpected first message from host: $message")
                }
            }
        }
    }
}
