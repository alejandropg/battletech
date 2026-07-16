package battletech.network

import battletech.network.server.GameServer
import battletech.network.transport.JsonLineConnection
import battletech.network.wire.ClientMessage
import battletech.network.wire.PROTOCOL_VERSION
import battletech.network.wire.ServerMessage
import battletech.network.wire.WireJson
import battletech.tactical.dice.RandomDiceRoller
import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.PlayerId
import battletech.tactical.session.BattleSession
import battletech.tactical.session.CommandResult
import battletech.tactical.session.MoveUnit
import kotlin.concurrent.thread
import kotlin.random.Random

/** A fresh session over the standard sample map/units, deterministic under seed 42. */
internal fun aSampleSession(): BattleSession = BattleSession(
    initialGameState = GameStateFactory().sampleGameState(),
    roller = RandomDiceRoller(Random(42L)),
)

/**
 * Runs [GameServer.attach] on its own daemon-ish test thread, since it
 * blocks (handshake read, then the reader loop) for the life of the
 * connection. Real-socket connections get one such thread per client from
 * [GameServer]'s accept loop; here the test plays that role. Wraps
 * [connection]'s raw pipes in [JsonLineConnection.Server] — the same adapter
 * the real accept path uses over a socket.
 */
internal fun GameServer.attachInBackground(
    connection: PipedConnection,
    onJoinAccepted: () -> Unit = {},
): Thread = thread(isDaemon = true, name = "test-server-attach") {
    attach(JsonLineConnection.Server(connection.serverInput, connection.serverOutput), onJoinAccepted)
}

/**
 * Test-setup helper: drives the host-side [GameServer.submitCommand] path
 * (legal moves for whichever player is currently active) until the movement
 * impulse sequence reaches [target]. Requires a client already connected
 * (host [GameServer.submitCommand] rejects otherwise) and the session
 * already in the movement phase.
 *
 * Returns the number of accepted setup moves. Each one enqueued a
 * [ServerMessage.StatePush] to the client — raw-wire tests must drain that
 * many pushes from the pipe before asserting on subsequent traffic.
 */
internal fun GameServer.advanceMovementUntilActivePlayerIs(target: PlayerId): Int {
    var moves = 0
    while (turnState.movement.activePlayer != target) {
        val active = turnState.movement.activePlayer
        val unit = turnState.selectableUnits(gameState).first()
        val reachability = viewFor(active).legalMovementsFor(unit.id).first()
        val destination = reachability.destinations.first()
        val result = submitCommand(MoveUnit(active, unit.id, destination, reachability.mode))
        check(result is CommandResult.Accepted) { "setup move for $active was rejected: $result" }
        moves++
    }
    return moves
}

/** Writes a [ClientMessage.Join] for [sessionId] and blocks for the raw first reply line. */
internal fun PipedConnection.sendJoin(sessionId: String, protocolVersion: Int = PROTOCOL_VERSION) {
    clientOutput.write(WireJson.encodeToLine(ClientMessage.Join(sessionId, protocolVersion)) + "\n")
    clientOutput.flush()
}

/**
 * Sends a [ClientMessage.Join] and reads exactly one reply line, decoded as a
 * [ServerMessage]. Use for handshake-only assertions (acceptance/rejection)
 * where no kickstart push is expected (rejections, or a rejoin after the
 * server's one-time kickstart has already fired).
 */
internal fun PipedConnection.join(sessionId: String, protocolVersion: Int = PROTOCOL_VERSION): ServerMessage {
    sendJoin(sessionId, protocolVersion)
    return WireJson.decodeServerMessage(clientInput.readLine())
}

/**
 * Sends a [ClientMessage.Join] and reads the two reply lines a *first-ever*
 * successful join produces: [ServerMessage.JoinAccepted] followed by the
 * kickstart's [ServerMessage.StatePush] (see [GameServer] KDoc — the
 * kickstart fires at most once per server lifetime).
 *
 * Host-config only (a single remote seat): the very first join already
 * completes [remoteSeats][GameServer], so it always kickstarts. For a
 * multi-seat (headless) server use [joinBothSeats] instead.
 */
internal fun PipedConnection.joinAndConsumeKickstart(sessionId: String): Pair<ServerMessage.JoinAccepted, ServerMessage.StatePush> {
    val joinAccepted = join(sessionId) as ServerMessage.JoinAccepted
    val push = WireJson.decodeServerMessage(clientInput.readLine()) as ServerMessage.StatePush
    return joinAccepted to push
}

/**
 * Two-seat join dance for a headless-config [GameServer] (`remoteSeats =
 * {PLAYER_1, PLAYER_2}`): joins [first] then [second], asserting the first
 * joiner is seated PLAYER_1 with a pre-kickstart snapshot and no push, and
 * the second is seated PLAYER_2 and receives the kickstart's
 * [ServerMessage.StatePush] — which also arrives on [first]'s connection,
 * since the kickstart fans out to every connected client.
 */
internal fun joinBothSeats(
    sessionId: String,
    first: PipedConnection,
    second: PipedConnection,
): TwoSeatJoin {
    val firstAccepted = first.join(sessionId) as ServerMessage.JoinAccepted
    val secondAccepted = second.join(sessionId) as ServerMessage.JoinAccepted
    val firstPush = WireJson.decodeServerMessage(first.clientInput.readLine()) as ServerMessage.StatePush
    val secondPush = WireJson.decodeServerMessage(second.clientInput.readLine()) as ServerMessage.StatePush
    return TwoSeatJoin(firstAccepted, firstPush, secondAccepted, secondPush)
}

/** Result of [joinBothSeats]: both joiners' handshake acceptance plus the kickstart push each received. */
internal data class TwoSeatJoin(
    val firstAccepted: ServerMessage.JoinAccepted,
    val firstKickstartPush: ServerMessage.StatePush,
    val secondAccepted: ServerMessage.JoinAccepted,
    val secondKickstartPush: ServerMessage.StatePush,
)

/** Polls [condition] until it's true, failing after [timeoutMs] instead of hanging forever. */
internal fun awaitTrue(timeoutMs: Long = 2_000, intervalMs: Long = 10, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        check(System.currentTimeMillis() < deadline) { "condition not met within ${timeoutMs}ms" }
        Thread.sleep(intervalMs)
    }
}
