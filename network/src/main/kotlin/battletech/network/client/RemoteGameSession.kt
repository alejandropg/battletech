package battletech.network.client

import battletech.network.wire.ClientMessage
import battletech.network.wire.GameSnapshot
import battletech.network.wire.JoinRejectionReason
import battletech.network.wire.PROTOCOL_VERSION
import battletech.network.wire.ServerMessage
import battletech.network.wire.SessionId
import battletech.network.wire.WireJson
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.DefaultPlayerView
import battletech.tactical.query.PlayerGameState
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
 * returns, [turnState]/[currentPhase] already reflect the accepted command.
 * Callers may read post-submit state immediately.
 *
 * **What this class does NOT have, on purpose:** raw [battletech.tactical.model.GameState].
 * [snapshot]`.gameState` is already [PlayerGameState] — [playerId]'s own projection, exactly
 * as the host computed and sent it (see [battletech.network.server.GameServer.snapshotFor]).
 *
 * That projection is enough to serve this seat completely, with no round trip: [stateFor],
 * [logFor] and [viewFor] all answer locally for [playerId]. [viewFor] in particular builds
 * the same [battletech.tactical.query.DefaultPlayerView] the authoritative host builds, over
 * the same [PlayerGameState] shape — the query engine
 * ([battletech.tactical.movement.ReachabilityCalculator], [battletech.tactical.query.WeaponTargeting],
 * [battletech.tactical.query.PhysicalAttackQueries]) consumes the projection, resolving the
 * ACTOR (always a unit this seat owns) through
 * [battletech.tactical.query.PlayerGameState.ownUnitById] and leaving every other unit
 * [battletech.tactical.unit.VisibleUnit]-shaped, because every field it reads off a
 * non-actor unit is public. One implementation, so a client's answer cannot drift from the
 * server's.
 *
 * None of the three can honestly answer for a DIFFERENT viewer — there is no raw state left
 * to re-project from — so all three refuse rather than guess; see their KDoc.
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
    private val listeners: MutableList<(GameEvent) -> Unit> = mutableListOf()
    private val pendingReply: ArrayBlockingQueue<ServerMessage.CommandReply> = ArrayBlockingQueue(1)
    private val requestIdCounter: AtomicLong = AtomicLong(0)
    private val readerThread: Thread

    @Volatile
    private var connectionLost: Boolean = false

    init {
        initial.log.forEach { log.append(it) }
        readerThread = thread(isDaemon = true, name = "remote-session-reader") { readLoop() }
    }

    public override val turnState: TurnState get() = snapshot.turnState
    public override val currentPhase: TurnPhase get() = snapshot.currentPhase
    public override val activePlayer: PlayerId? get() = snapshot.activePlayer
    public override val isMatchOver: Boolean get() = snapshot.isMatchOver
    public override val gameLog: GameLog get() = log

    /**
     * Answers "what is legal right now?" for THIS connection's own seat, locally, with no
     * round trip: [DefaultPlayerView] consumes the same [PlayerGameState] projection the
     * host's [battletech.tactical.session.BattleSession.viewFor] feeds it, so this client
     * runs the identical query code and cannot drift from the server's answer.
     *
     * Refuses any OTHER seat, for the same reason as [stateFor]: this replica holds only its
     * own projection, so it has neither the data nor the standing to build the opponent's
     * view.
     */
    public override fun viewFor(playerId: PlayerId): PlayerView {
        require(playerId == this.playerId) {
            "RemoteGameSession.viewFor: this replica can only build a view for its own seat " +
                "(${this.playerId}); it holds no projection for $playerId."
        }
        return DefaultPlayerView(playerId, snapshot.gameState, snapshot.turnState)
    }

    /**
     * Serves ONLY [playerId] (this connection's own seat): [snapshot]`.gameState` already
     * IS that projection, exactly as the host built and sent it, so no re-projection
     * happens here. Any other [viewer] — including `null` — throws rather than guess: this
     * class holds no raw state to re-project from, so returning [snapshot]`.gameState`
     * unchanged for a different viewer would silently hand back the wrong player's shape
     * under a false label, and returning it for `null` would misrepresent "I don't know who
     * is looking" as "here is player X's view" — both are the "return the wrong thing
     * silently" this design explicitly rules out.
     */
    public override fun stateFor(viewer: PlayerId?): PlayerGameState {
        require(viewer == playerId) {
            "RemoteGameSession.stateFor: this replica only holds $playerId's own projection " +
                "(from the host's snapshot); it cannot serve viewer=$viewer without raw state to re-project from."
        }
        return snapshot.gameState
    }

    /**
     * Serves ONLY [playerId], same rule as [stateFor]: [log] already holds the host's
     * [battletech.tactical.session.GameEvent.redactFor]-filtered entries for THIS seat
     * (see [battletech.network.server.GameServer.snapshotFor]'s KDoc for the outbound
     * paths that redact it before it ever reaches [readLoop]), so it's returned as-is
     * rather than re-redacted for a viewer this class has no raw state to check against.
     */
    public override fun logFor(viewer: PlayerId?): List<LogEntry> {
        require(viewer == playerId) {
            "RemoteGameSession.logFor: this replica only holds $playerId's own redacted log; " +
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
        val snapshotOfListeners = listeners.toList()
        for (listener in snapshotOfListeners) listener(event)
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
