package battletech.tui

import battletech.network.client.JoinRejectedException
import battletech.network.client.ClientGameSession
import battletech.network.server.GameServer
import battletech.network.server.SocketAcceptor
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.PlayerId
import battletech.tactical.model.map.MapLoadException
import battletech.tactical.model.map.resolveMap
import battletech.tactical.query.projectFor
import battletech.tactical.session.GameEvent
import battletech.tui.view.GameLogFormatter
import java.io.IOException
import java.util.concurrent.CountDownLatch

/**
 * Resolves a `--map` built-in name or existing path (or [DEFAULT_MAP_NAME] when absent) to a
 * [battletech.tactical.model.GameMap]. On [MapLoadException], prints the message to stderr and
 * exits 2 with no stack trace.
 */
private fun resolveMapOrExit(mapName: String?) = try {
    resolveMap(mapName ?: DEFAULT_MAP_NAME)
} catch (e: MapLoadException) {
    System.err.println(e.message)
    kotlin.system.exitProcess(2)
}

/** Bound on [awaitKickstart]'s poll loop — generous for an in-process, no-I/O handoff. */
private const val KICKSTART_TIMEOUT_MS: Long = 2000

/**
 * Blocks until every hot-seat [seats] session has caught up with [server]'s current phase.
 *
 * [GameServer.attach] sends a seat its [battletech.network.wire.ServerMessage.JoinAccepted]
 * BEFORE calling [battletech.tactical.session.BattleSession.advance] (see [GameServer]'s KDoc
 * on kickstart) — true even for the second [GameServer.connectLocal] call, the one whose join
 * completes the roster and triggers the advance. The post-kickstart state reaches each seat
 * afterward as a [battletech.network.wire.ServerMessage.StatePush], applied asynchronously by
 * that seat's own reader thread. A [GameServer.connectLocal] call can therefore return before
 * its own session reflects the kickstart, and the OTHER already-connected seat's session lags
 * the same way. [TuiApp] reads `currentPhase` to build its initial `AppState` and is not the
 * place to absorb transport timing — so composition waits here first: [server]`.currentPhase`
 * is read under the server's own lock (see [GameServer.currentPhase]), which cannot return
 * until [GameServer.attach]'s synchronized block — kickstart included — has finished, so it is
 * always the post-kickstart ground truth to converge every seat against.
 */
private fun awaitKickstart(server: GameServer, seats: Map<PlayerId, ClientGameSession>) {
    val deadline = System.nanoTime() + KICKSTART_TIMEOUT_MS * 1_000_000L
    while (seats.values.any { it.currentPhase != server.currentPhase }) {
        check(System.nanoTime() < deadline) {
            "hot-seat kickstart did not land within ${KICKSTART_TIMEOUT_MS}ms: " +
                "server=${server.currentPhase}, seats=${seats.mapValues { it.value.currentPhase }}"
        }
        Thread.sleep(1)
    }
}

/**
 * Entry point for the TUI application.
 * Processes command-line arguments and launches the [TuiApp].
 */
public fun main(args: Array<String>) {
    val mode = parseArgs(args)

    when (mode) {
        is Mode.Local -> {
            val map = resolveMapOrExit(mode.mapName)
            val server = GameServer.host(GameStateFactory().sampleGameState(map))
            // Build the map from each returned session's OWN playerId — connectLocal() assigns
            // seats via (allSeats - clients.keys).min(), not call order, so the Nth call is not
            // guaranteed to be the Nth PlayerId. See GameServer.connectLocal's KDoc.
            val seats = List(PlayerId.entries.size) { server.connectLocal() }.associateBy { it.playerId }
            check(seats.keys == PlayerId.entries.toSet()) {
                "hot-seat roster incomplete: expected ${PlayerId.entries.toSet()}, got ${seats.keys}"
            }
            // TuiApp reads currentPhase to build its initial AppState, so composition must
            // absorb the kickstart race here — see awaitKickstart's KDoc.
            awaitKickstart(server, seats)
            server.use { TuiApp(seats, mode.theme).run() }
        }

        is Mode.Host -> {
            val map = resolveMapOrExit(mode.mapName)
            val server = GameServer.host(GameStateFactory().sampleGameState(map))
            // connectLocal() BEFORE the acceptor starts — see GameServer.connectLocal's KDoc
            // for why that order is what makes the local seat deterministically PLAYER_1.
            val localSession = server.connectLocal()
            val acceptor = SocketAcceptor(server, mode.port)
            acceptor.start()
            println("Session ID: ${server.sessionId} — listening on port ${acceptor.boundPort}")
            acceptor.use {
                server.use {
                    TuiApp(seats = mapOf(localSession.playerId to localSession), theme = mode.theme).run()
                }
            }
        }

        is Mode.Join -> {
            val remote = try {
                ClientGameSession.connect(mode.host, mode.port, mode.sessionId)
            } catch (e: JoinRejectedException) {
                System.err.println("Join rejected: ${e.reason}")
                kotlin.system.exitProcess(1)
            } catch (e: IOException) {
                System.err.println("Could not connect to ${mode.host}:${mode.port}: ${e.message}")
                kotlin.system.exitProcess(1)
            }
            remote.use { remote ->
                TuiApp(seats = mapOf(remote.playerId to remote), theme = mode.theme).run()
            }
        }

        is Mode.Server -> runHeadlessServer(mode.port, resolveMapOrExit(mode.mapName))
    }
}

/**
 * Headless dedicated server: no [TuiApp], no Mordant terminal. Both players connect
 * remotely via the `join` subcommand. Runs until Ctrl-C (or another SIGTERM), printing every game
 * event to stdout as it happens; the process stays up after [battletech.tactical.session.MatchEnded].
 */
private fun runHeadlessServer(port: Int, map: GameMap) {
    val server = GameServer.host(GameStateFactory().sampleGameState(map))

    val printer = GameEventPrinter(System.out)
    // Replay the seeded notices before subscribing so the printer sees the whole log from the
    // start without racing the accept loop — see GameServer.host's KDoc for why this is safe.
    server.gameLog.snapshot().forEach { entry -> printer.print(entry.event, server.gameState, entry.turn) }
    server.subscribe { event ->
        printer.print(event, server.gameState, server.turnState.turnNumber)
    }

    val acceptor = SocketAcceptor(server, port)
    acceptor.start()
    println("Session ID: ${server.sessionId} — listening on port ${acceptor.boundPort}")

    val latch = CountDownLatch(1)
    Runtime.getRuntime().addShutdownHook(
        Thread {
            acceptor.close()
            server.close()
            latch.countDown()
        },
    )
    latch.await()
}

/**
 * Renders [GameEvent]s to an [Appendable] as human-readable log lines, printing a
 * `== TURN n ==` header whenever the turn number changes from the last-printed event.
 * Formatting is delegated to the tui-internal [GameLogFormatter], which every other
 * surface (the in-game log panel) also uses, so the console output matches what a
 * connected player's TUI shows.
 *
 * [print] is synchronized because, per [battletech.network.server.GameServer]'s threading
 * model, subscription listeners fire on whatever thread mutates the session (client
 * reader threads under the server's lock) — the only mutable state here is [lastPrintedTurn].
 */
internal class GameEventPrinter(private val out: Appendable) {
    private var lastPrintedTurn: Int? = null

    /**
     * There is no single "viewer" to project [gameState] for here, so it is revealed in full
     * via [projectFor] rather than redacted for an arbitrary seat. [GameLogFormatter] itself
     * takes the same [PlayerGameState] shape every other consumer (the in-game LOG panel) does.
     */
    @Synchronized
    fun print(event: GameEvent, gameState: GameState, turnNumber: Int) {
        val lines = GameLogFormatter.lines(event, gameState.projectFor(viewer = null, revealAll = true))
        if (lines.isEmpty()) return
        if (turnNumber != lastPrintedTurn) {
            out.append("== TURN $turnNumber ==\n")
            lastPrintedTurn = turnNumber
        }
        lines.forEach { line -> out.append("${line.icon ?: ">"} ${line.text}\n") }
    }
}
