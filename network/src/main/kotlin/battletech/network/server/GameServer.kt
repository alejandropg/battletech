package battletech.network.server

import battletech.network.client.ClientGameSession
import battletech.network.transport.InMemoryConnection
import battletech.network.transport.JsonLineConnection
import battletech.network.transport.ServerConnection
import battletech.network.wire.ClientMessage
import battletech.network.wire.GameSnapshot
import battletech.network.wire.JoinRejectionReason
import battletech.network.wire.PROTOCOL_VERSION
import battletech.network.wire.ServerMessage
import battletech.network.wire.SessionId
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
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Host-side network endpoint: wraps an authoritative [BattleSession] and exposes it to every
 * seat ([PlayerId.entries]) as a client connection over the [battletech.network.transport]
 * port — a real socket ([JsonLineConnection]) via [SocketAcceptor], or an in-process
 * [InMemoryConnection] via [connectLocal]. Every seat is a connection; there is no seat this
 * class serves any other way. That is deliberate — see below.
 *
 * ### There is exactly one path in
 *
 * Every command, from every seat, arrives the same way: a [ClientMessage.SubmitCommand] read by
 * [runReaderLoop] off that seat's [ServerConnection]/wire, checked against that seat's assigned
 * [ConnectedClient.seat], and applied via [submitAndPush]. A caller driving the local player
 * used to reach [session] through a second, hand-rolled path (a `submitCommand` override on this
 * class, gated by a hardcoded `PlayerId.PLAYER_1` check) that could disagree with the remote
 * path's seat check — remote enforcement was derived from the connection's assigned seat, local
 * enforcement was a literal constant, and nothing kept the two in sync. [connectLocal] closes
 * that gap by making the local player a client too: it hands back a [ClientGameSession] wired to
 * an [InMemoryConnection] half, indistinguishable on this end from a socket client's connection.
 * One path, one seat-check, one place the guarantee ("neither side can act as another seat's")
 * can live.
 *
 * ### What this class does NOT own
 *
 * No [java.net.ServerSocket], no accept loop, no port. [SocketAcceptor] owns all three and calls
 * [attach] once a socket connection completes the wire framing — see its KDoc for why splitting
 * that out is what makes hot-seat (a [GameServer] with two [connectLocal] clients and no socket
 * at all) possible. This class is no longer a [GameSession] either: there is no single local
 * seat left to implement that interface's `submitCommand`/`turnState`/etc. *for* — every seat's
 * own [connectLocal]/[JsonLineConnection] client already implements [GameSession] for itself.
 * What legitimately remains here — [gameState], [turnState], [currentPhase], [activePlayer],
 * [isMatchOver], [gameLog], [viewFor], [stateFor], [logFor], [subscribe] — are plain (non-
 * override) reads of the authoritative session: the headless console (no per-seat viewer to
 * project for) and tests that need to assert against ground truth alongside a client's
 * projection both have a legitimate need for them that no [GameSession] implementation could
 * serve.
 *
 * ### Kickstart: fires once, no matter who completes the roster
 *
 * [BattleSession.advance] must run exactly once per server lifetime, the moment every seat in
 * [PlayerId.entries] has attached for the first time — regardless of whether that seat is a
 * [connectLocal] client or a socket client. [attach] is that single trigger point (`kickstarted`
 * inside its synchronized block), so this holds uniformly across all three compositions:
 * - **hot-seat**: two [connectLocal] calls; the second one's [attach] call completes the roster.
 * - **`--host`**: one [connectLocal] call + one socket join; whichever completes the roster
 *   fires it — see [connectLocal]'s KDoc for why the local seat is guaranteed to be first.
 * - **`--server`**: two socket joins; the second fires it.
 *
 * The `everStarted` flag (checked and set inside the same synchronized block that runs
 * [BattleSession.advance]) makes a second firing impossible, and [disconnect]'s identity check
 * (not just a seat check) means a rejoin can never be mistaken for a first-time attach — a
 * rejoin's seat is already a key in [clients] at the moment of eviction, so [attach]'s "first
 * time every seat is filled" condition (`clients.keys == allSeats` guarded by `!everStarted`)
 * can only ever be satisfied once, on the roster's true first completion.
 *
 * ### Freeze: correct even though an in-memory seat can't vanish
 *
 * Both [runReaderLoop]'s seat check and [attach]'s handshake reject with
 * [CommandRejection.OpponentUnavailable] (or [JoinRejectionReason.SEAT_TAKEN]/handshake
 * behavior, respectively) whenever [clients] doesn't yet cover every seat in [PlayerId.entries].
 * That check needs no special case per transport: an [InMemoryConnection] simply never drops on
 * its own (nothing closes it unless a caller explicitly calls `close()`, which no in-game code
 * path does), so a hot-seat server's roster — once full — can never become partial again, and the
 * freeze branch is simply unreachable there. A socket seat CAN drop (a real disconnect), and the
 * exact same check freezes the whole session for every remaining seat, [connectLocal] ones
 * included, until [attach] sees that seat rejoin.
 *
 * [snapshotFor] is the one seam every outbound [GameSnapshot] (join acceptance and pushes alike)
 * is built through — see its KDoc for how redaction happens there, and why the log traveling
 * alongside it ([ServerMessage.JoinAccepted.log], [ServerMessage.StatePush.entries]) must redact
 * in lockstep.
 */
public class GameServer(
    private val session: BattleSession,
    public val sessionId: String,
) : AutoCloseable {

    private val lock: Any = Any()

    /** Every seat this match expects a connection for — no distinction between "local" and "remote" seats anymore. */
    private val allSeats: Set<PlayerId> = PlayerId.entries.toSet()

    private val clients: MutableMap<PlayerId, ConnectedClient> = mutableMapOf()
    private var everStarted: Boolean = false

    // ---------- reads of the authoritative session ----------

    /**
     * The authoritative, unredacted state — NOT part of [GameSession] (a client only ever holds
     * its own projection). Legitimate here because [GameServer] runs in-process with [session]:
     * this never crosses the wire itself, only the per-seat projections built from it do (see
     * [snapshotFor]). Used by the headless console (there is no single "viewer" to project for)
     * and by tests that need to inspect/drive the authoritative state directly.
     */
    public val gameState: GameState get() = synchronized(lock) { session.gameState }
    public val turnState: TurnState get() = synchronized(lock) { session.turnState }
    public val currentPhase: TurnPhase get() = synchronized(lock) { session.currentPhase }
    public val activePlayer: PlayerId? get() = synchronized(lock) { session.activePlayer }
    public val isMatchOver: Boolean get() = synchronized(lock) { session.isMatchOver }
    public val gameLog: GameLog get() = synchronized(lock) { session.gameLog }

    public fun viewFor(playerId: PlayerId): PlayerView = synchronized(lock) { session.viewFor(playerId) }

    public fun stateFor(viewer: PlayerId?): PlayerGameState = synchronized(lock) { session.stateFor(viewer) }

    public fun logFor(viewer: PlayerId?): List<LogEntry> = synchronized(lock) { session.logFor(viewer) }

    /**
     * Register [listener] to receive every raw event emitted by [session] — session-wide and
     * unfiltered, the same as [GameSession.subscribe]'s documented contract. The headless
     * console (`GameEventPrinter` in `battletech.tui`) is the one legitimate direct caller: it
     * has no single seat to redact for, and reveals everything on purpose. Listener bodies must
     * be thread-safe on the caller's side — dispatch may occur on a client's reader thread (in
     * response to a remote command) as well as any other thread that ends up inside
     * [submitAndPush].
     */
    public fun subscribe(listener: (GameEvent) -> Unit): Subscription = session.subscribe(listener)

    /**
     * Seats the local player as a client of this server — the fix this commit makes. Builds an
     * [InMemoryConnection.pair], hands the server half to [attach] on its own daemon thread
     * (exactly as [SocketAcceptor] hands a socket connection its own thread), and performs on the
     * client half the SAME [ClientMessage.Join] handshake a socket client performs
     * ([ClientGameSession.handshake]). The seat this call is assigned, the snapshot it starts
     * with, and every event it receives afterward are therefore produced by the identical code
     * path a `--join`ed client goes through — there is no way for the returned session to behave
     * differently from a socket client's, because it isn't a different implementation, it's the
     * same one over a different [battletech.network.transport.ClientConnection].
     *
     * Returns [ClientGameSession] rather than [GameSession] for the same reason
     * [ClientGameSession.connect] does: a caller needs the seat the server assigned
     * ([ClientGameSession.playerId]) to know which seat it is playing, and widening the return
     * type here would only force that caller to downcast to get it back.
     *
     * **Determinism for `--host`:** seat assignment is `(allSeats - clients.keys).min()` — first
     * attach wins [PlayerId.PLAYER_1]. [Companion.host] deliberately does not start any
     * [SocketAcceptor] itself, so as long as a caller calls this method before constructing (or
     * at least before [SocketAcceptor.start]ing) an acceptor for the same server, no socket
     * client can possibly attach first: there is no accept loop running yet to race. That's the
     * whole determinism argument — it depends on call order, not on locking, because nothing
     * else could observe [clients] before the acceptor exists.
     */
    public fun connectLocal(): ClientGameSession {
        val (serverHalf, clientHalf) = InMemoryConnection.pair()
        thread(isDaemon = true, name = "game-server-local") {
            attach(serverHalf)
        }
        return ClientGameSession.handshake(clientHalf, sessionId)
    }

    /** Disconnects every currently-connected seat (local and remote alike) — see [disconnect]. */
    public override fun close() {
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
     * or actively cheating. A [connectLocal] seat crosses this same seam too
     * (it is a real [ServerConnection]/[battletech.network.transport.ClientConnection]
     * pair, just an in-process one) — it is simply never adversarial in practice.
     *
     * [seat] gets [session]'s state PROJECTED for it, via [BattleSession.stateFor]
     * — the same seam [BattleSession.stateFor] uses for the in-process TUI, so a
     * connected seat can see no more of another seat's units than a hot-seat
     * viewer could. This is only ONE of three outbound paths that must agree:
     * [ServerMessage.JoinAccepted] carries [BattleSession.logFor] (not the raw
     * [session]`.gameLog.snapshot()`), and every [ServerMessage.StatePush]
     * carries a log delta run through [redactedDeltaFor] — see [attach]
     * and [submitAndPush]. Redacting only this snapshot and leaving either log
     * channel un-redacted leaks everything through that other channel; that
     * mismatch is exactly why the previous (deleted) redaction attempt never
     * worked. No sentinel/fake values are used anywhere in this path — a
     * [PlayerGameState] simply doesn't have a field to leak for units [seat]
     * doesn't own ([battletech.tactical.unit.ForeignUnit] has no
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
     * log [BattleSession.logFor] redacts) for [seat], entry by entry via
     * [battletech.tactical.session.redactFor] — same rule, same [session]`.gameState` used
     * for ownership lookups, same [BattleSession.isMatchOver] reveal, just scoped to the
     * entries that changed since the last push instead of the full history.
     */
    private fun redactedDeltaFor(entries: List<LogEntry>, seat: PlayerId): List<LogEntry> =
        entries.mapNotNull { entry ->
            entry.event.redactFor(seat, session.gameState, revealAll = session.isMatchOver)?.let { entry.copy(event = it) }
        }

    // ---------- transport ----------

    /**
     * Performs the join handshake on [connection] and, on success, runs the
     * per-client reader loop inline (blocking the calling thread until
     * disconnect). Shared by [SocketAcceptor] (each real socket connection gets
     * its own daemon thread), [connectLocal] (same — its own daemon thread over
     * an in-process connection instead of a socket), and pipe-based tests (the
     * test spawns the thread itself). [onJoinAccepted] fires once, right after a
     * successful handshake — [SocketAcceptor] uses it to clear the handshake
     * `soTimeout`; there is no `onDisconnect` counterpart because tearing the
     * transport down is just [ServerConnection.close] now, called from [disconnect].
     */
    internal fun attach(
        connection: ServerConnection,
        onJoinAccepted: () -> Unit = {},
    ) {
        val join = connection.receive() as? ClientMessage.Join ?: run {
            connection.close()
            return
        }

        val rejection = synchronized(lock) {
            when {
                clients.keys.containsAll(allSeats) -> JoinRejectionReason.SEAT_TAKEN
                !SessionId.matches(join.sessionId, sessionId) -> JoinRejectionReason.UNKNOWN_SESSION
                join.protocolVersion != PROTOCOL_VERSION -> JoinRejectionReason.INCOMPATIBLE_PROTOCOL
                else -> null
            }
        }

        if (rejection != null) {
            connection.send(ServerMessage.JoinRejected(rejection))
            connection.close()
            return
        }

        onJoinAccepted()

        val connected = synchronized(lock) {
            val seat = (allSeats - clients.keys).min()
            val client = ConnectedClient(seat = seat, connection = connection)
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

            val kickstarted = !everStarted && clients.keys == allSeats
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

        runReaderLoop(connected)
    }

    private fun runReaderLoop(connected: ConnectedClient) {
        try {
            while (true) {
                val incoming = connected.connection.receive() ?: break
                val message = incoming as? ClientMessage.SubmitCommand ?: continue
                synchronized(lock) {
                    val result = when {
                        !clients.keys.containsAll(allSeats) ->
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
        } finally {
            disconnect(connected)
        }
    }

    private fun startWriterThread(connected: ConnectedClient) {
        connected.writerThread = thread(isDaemon = true, name = "game-server-write") {
            try {
                while (true) {
                    val message = connected.outbound.take()
                    connected.connection.send(message)
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
                connected.connection.close()
            } catch (e: IOException) {
                // already gone
            }
            session.annotate(SessionNotice("${seatLabel(connected.seat)} disconnected — waiting for rejoin…"))
        }
    }

    private fun seatLabel(seat: PlayerId): String = "Player ${seat.ordinal + 1}"

    private class ConnectedClient(
        internal val seat: PlayerId,
        internal val connection: ServerConnection,
    ) {
        internal val outbound: LinkedBlockingQueue<ServerMessage> = LinkedBlockingQueue()
        internal var writerThread: Thread? = null
    }

    public companion object {

        /**
         * Builds the [BattleSession] + [SessionId] + [GameServer] triple every launcher needs,
         * seeded with the two notices ("Session ID: …", "Waiting for players to join…") every
         * launcher prints. Takes [GameState], not a [battletech.tactical.model.GameMap] or a
         * [battletech.tactical.model.GameStateFactory] call — map/scenario resolution is a `tui`
         * concern ([battletech.network] must not depend on [battletech.tactical.model.GameStateFactory]
         * for that), so callers pass `GameStateFactory().sampleGameState(map)` themselves.
         *
         * Takes no port: this class no longer binds a socket at all — see the class KDoc's "What
         * this class does NOT own". A caller that wants a listening socket constructs a
         * [SocketAcceptor] over the returned server itself; a caller that wants a local seat
         * calls [connectLocal]. Neither is implied by [host] — do both, either, or neither.
         *
         * Deliberately does NOT call [connectLocal] or start any [SocketAcceptor] — the returned
         * server has no attached clients yet, so no [GameEvent] can fire, which makes "replay the
         * seeded notices, subscribe, then attach clients" race-free for any caller that wants to
         * observe them: replaying [GameServer.gameLog] (or [logFor]) before subscribing can never
         * miss an event to a subscriber that races a client attaching, because nothing has
         * attached yet. Callers that don't need to observe the seed notices before traffic starts
         * (a host UI reading them back out of the replayed log) can just attach immediately.
         */
        public fun host(initialGameState: GameState): GameServer {
            val session = BattleSession(initialGameState = initialGameState, initialTurnState = TurnState.NULL)
            val sessionId = SessionId.generate()
            val server = GameServer(session, sessionId)
            session.annotate(SessionNotice("Session ID: $sessionId"))
            session.annotate(SessionNotice("Waiting for players to join…"))
            return server
        }
    }
}
