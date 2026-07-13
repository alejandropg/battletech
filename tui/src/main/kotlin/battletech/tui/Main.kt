package battletech.tui

import battletech.network.client.JoinRejectedException
import battletech.network.client.RemoteGameSession
import battletech.network.server.GameServer
import battletech.network.wire.SessionId
import battletech.tactical.model.GameState
import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.PlayerId
import battletech.tactical.session.BattleSession
import battletech.tactical.session.GameEvent
import battletech.tactical.session.SessionNotice
import battletech.tactical.session.TurnState
import battletech.tui.view.GameLogFormatter
import java.io.IOException
import java.util.concurrent.CountDownLatch

/** Default TCP port for `--host`/`--join`/`--server` when `--port`/an explicit port is not supplied. */
internal const val DEFAULT_PORT: Int = 2470

/**
 * The four ways the TUI can be launched, resolved from CLI args by [parseArgs].
 * [Local] is today's hot-seat behavior (both players share one terminal).
 * [Host] starts a [GameServer] and seats the local player as [PlayerId.PLAYER_1].
 * [Join] connects to a remote host and seats the local player as whatever seat the server assigns.
 * [Server] starts a headless dedicated server — no TUI — and both players connect via [Join].
 */
internal sealed interface Mode {
    data object Local : Mode
    data class Host(val port: Int = DEFAULT_PORT) : Mode
    data class Join(val host: String, val port: Int = DEFAULT_PORT, val sessionId: String) : Mode
    data class Server(val port: Int = DEFAULT_PORT) : Mode
}

/** Thrown by [parseArgs] for any unrecognized or malformed argument combination. */
internal class ArgsException(message: String) : Exception(message)

/**
 * Hand-rolled CLI parser (no args = hot-seat). Kept as a pure function —
 * throws [ArgsException] on any malformed input — so it's unit-testable
 * without touching stderr/exit; [main] is the only caller that turns a
 * thrown [ArgsException] into a usage message + `exit(2)`.
 *
 * Syntax:
 * - (no args): [Mode.Local]
 * - `--host [--port N]`: [Mode.Host]
 * - `--join <ip[:port]> --session <id>`: [Mode.Join]
 * - `--server [--port N]`: [Mode.Server]
 */
internal fun parseArgs(args: Array<String>): Mode {
    if (args.isEmpty()) return Mode.Local

    return when (args[0]) {
        "--host" -> parseHost(args)
        "--join" -> parseJoin(args)
        "--server" -> parseServer(args)
        else -> throw ArgsException("Unknown argument: ${args[0]}")
    }
}

private fun parseHost(args: Array<String>): Mode.Host {
    var port = DEFAULT_PORT
    var i = 1
    while (i < args.size) {
        when (args[i]) {
            "--port" -> {
                val value = args.getOrNull(i + 1) ?: throw ArgsException("--port requires a value")
                port = value.toIntOrNull() ?: throw ArgsException("--port must be an integer, got: $value")
                i += 2
            }
            else -> throw ArgsException("Unknown argument: ${args[i]}")
        }
    }
    return Mode.Host(port = port)
}

private fun parseServer(args: Array<String>): Mode.Server {
    var port = DEFAULT_PORT
    var i = 1
    while (i < args.size) {
        when (args[i]) {
            "--port" -> {
                val value = args.getOrNull(i + 1) ?: throw ArgsException("--port requires a value")
                port = value.toIntOrNull() ?: throw ArgsException("--port must be an integer, got: $value")
                i += 2
            }
            else -> throw ArgsException("Unknown argument: ${args[i]}")
        }
    }
    return Mode.Server(port = port)
}

private fun parseJoin(args: Array<String>): Mode.Join {
    val hostArg = args.getOrNull(1) ?: throw ArgsException("--join requires a host argument")
    var sessionId: String? = null
    var i = 2
    while (i < args.size) {
        when (args[i]) {
            "--session" -> {
                sessionId = args.getOrNull(i + 1) ?: throw ArgsException("--session requires a value")
                i += 2
            }
            else -> throw ArgsException("Unknown argument: ${args[i]}")
        }
    }
    val id = sessionId ?: throw ArgsException("--join requires --session <id>")

    val colonIndex = hostArg.indexOf(':')
    val (host, port) = if (colonIndex == -1) {
        hostArg to DEFAULT_PORT
    } else {
        val hostPart = hostArg.substring(0, colonIndex)
        val portPart = hostArg.substring(colonIndex + 1)
        if (hostPart.isEmpty()) throw ArgsException("Malformed --join host: $hostArg")
        val parsedPort = portPart.toIntOrNull() ?: throw ArgsException("Malformed --join port: $hostArg")
        hostPart to parsedPort
    }
    if (host.isEmpty()) throw ArgsException("Malformed --join host: $hostArg")

    return Mode.Join(host = host, port = port, sessionId = id)
}

private fun printUsageAndExit(message: String): Nothing {
    System.err.println(message)
    System.err.println(
        """
        Usage:
          battletech-tui                                  hot-seat (both players share this terminal)
          battletech-tui --host [--port N]                host a session (default port $DEFAULT_PORT)
          battletech-tui --join <ip[:port]> --session <id> join a hosted session
          battletech-tui --server [--port N]               headless dedicated server (both players join remotely)
        """.trimIndent(),
    )
    kotlin.system.exitProcess(2)
}

/**
 * Entry point for the TUI application.
 * Processes command-line arguments and launches the [TuiApp].
 */
public fun main(args: Array<String>) {
    val mode = try {
        parseArgs(args)
    } catch (e: ArgsException) {
        printUsageAndExit(e.message ?: "Invalid arguments")
    }

    when (mode) {
        is Mode.Local -> TuiApp().run()

        is Mode.Host -> {
            val session = BattleSession(
                initialGameState = GameStateFactory().sampleGameState(),
                initialTurnState = TurnState.NULL,
            )
            val sessionId = SessionId.generate()
            val server = GameServer(session, sessionId, mode.port)
            server.start()
            println("Session ID: $sessionId — listening on port ${server.boundPort}")
            session.annotate(SessionNotice("Session ID: $sessionId"))
            session.annotate(SessionNotice("Waiting for opponent to join…"))
            server.use { server ->
                TuiApp(
                    providedSession = server,
                    localPlayer = PlayerId.PLAYER_1,
                ).run()
            }
        }

        is Mode.Join -> {
            val remote = try {
                RemoteGameSession.connect(mode.host, mode.port, mode.sessionId)
            } catch (e: JoinRejectedException) {
                System.err.println("Join rejected: ${e.reason}")
                kotlin.system.exitProcess(1)
            } catch (e: IOException) {
                System.err.println("Could not connect to ${mode.host}:${mode.port}: ${e.message}")
                kotlin.system.exitProcess(1)
            }
            remote.use { remote ->
                TuiApp(
                    providedSession = remote,
                    localPlayer = remote.playerId,
                ).run()
            }
        }

        is Mode.Server -> runHeadlessServer(mode.port)
    }
}

/**
 * Headless dedicated server: no [TuiApp], no Mordant terminal. Both players connect
 * remotely via `--join`. Runs until Ctrl-C (or another SIGTERM), printing every game
 * event to stdout as it happens; the process stays up after [battletech.tactical.session.MatchEnded].
 */
private fun runHeadlessServer(port: Int) {
    val session = BattleSession(
        initialGameState = GameStateFactory().sampleGameState(),
        initialTurnState = TurnState.NULL,
    )
    val sessionId = SessionId.generate()

    val printer = GameEventPrinter(System.out)
    // Subscribed before any annotation so the printer sees the whole log from the start.
    // EventVisibility.filterFor is currently a passthrough (it returns every event
    // unchanged for every player), so a single PLAYER_1 subscription happens to see both
    // players' events. A future hidden-info redaction pass on EventVisibility must revisit
    // this printer — once filterFor actually redacts, PLAYER_1's view will stop being "the
    // whole game" and this stdout console will need its own non-player-scoped feed.
    session.subscribe(PlayerId.PLAYER_1) { event ->
        printer.print(event, session.gameState, session.turnState.turnNumber)
    }

    session.annotate(SessionNotice("Session ID: $sessionId"))
    session.annotate(SessionNotice("Waiting for players to join…"))

    val server = GameServer(session, sessionId, port, remoteSeats = setOf(PlayerId.PLAYER_1, PlayerId.PLAYER_2))
    server.start()
    println("Session ID: $sessionId — listening on port ${server.boundPort}")

    val latch = CountDownLatch(1)
    Runtime.getRuntime().addShutdownHook(
        Thread {
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

    @Synchronized
    fun print(event: GameEvent, gameState: GameState, turnNumber: Int) {
        val lines = GameLogFormatter.lines(event, gameState)
        if (lines.isEmpty()) return
        if (turnNumber != lastPrintedTurn) {
            out.append("== TURN $turnNumber ==\n")
            lastPrintedTurn = turnNumber
        }
        lines.forEach { line -> out.append("${line.icon ?: ">"} ${line.text}\n") }
    }
}
