package battletech.tui

import battletech.network.client.JoinRejectedException
import battletech.network.client.RemoteGameSession
import battletech.network.server.GameServer
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

/** Default TCP port for `--host`/`--join`/`--server` when `--port`/an explicit port is not supplied. */
internal const val DEFAULT_PORT: Int = 2470

/** Built-in map id used when `--map` is not supplied. */
internal const val DEFAULT_MAP_NAME: String = "default"

/**
 * The four ways the TUI can be launched, resolved from CLI args by [parseArgs].
 * [Local] is today's hot-seat behavior (both players share one terminal).
 * [Host] starts a [GameServer] and seats the local player as [PlayerId.PLAYER_1].
 * [Join] connects to a remote host and seats the local player as whatever seat the server assigns.
 * [Server] starts a headless dedicated server — no TUI — and both players connect via [Join].
 */
internal sealed interface Mode {
    data class Local(val mapName: String? = null) : Mode
    data class Host(val port: Int = DEFAULT_PORT, val mapName: String? = null) : Mode
    data class Join(val host: String, val port: Int = DEFAULT_PORT, val sessionId: String) : Mode
    data class Server(val port: Int = DEFAULT_PORT, val mapName: String? = null) : Mode
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
 * - `--host [--port N] [--map <name|path>]`: [Mode.Host]
 * - `--join <ip[:port]> --session <id>`: [Mode.Join]
 * - `--server [--port N] [--map <name|path>]`: [Mode.Server]
 * - `[--map <name|path>]` may additionally appear anywhere for hot-seat/host/server (invalid with `--join`).
 */
internal fun parseArgs(args: Array<String>): Mode {
    val (mapName, rest) = extractMapArg(args)

    if (rest.isEmpty()) return Mode.Local(mapName = mapName)

    return when (rest[0]) {
        "--host" -> Mode.Host(parsePort(rest), mapName)
        "--join" -> {
            if (mapName != null) throw ArgsException("--map cannot be combined with --join")
            parseJoin(rest)
        }
        "--server" -> Mode.Server(parsePort(rest), mapName)
        else -> throw ArgsException("Unknown argument: ${rest[0]}")
    }
}

/**
 * Pulls the first `--map <value>` pair out of [args], wherever it appears, returning the map
 * name (or null if absent) alongside the remaining args in their original relative order.
 * Throws [ArgsException] if `--map` is present with no following value.
 */
private fun extractMapArg(args: Array<String>): Pair<String?, Array<String>> {
    val index = args.indexOf("--map")
    if (index == -1) return null to args

    val value = args.getOrNull(index + 1) ?: throw ArgsException("--map requires a value")
    val remaining = args.filterIndexed { i, _ -> i != index && i != index + 1 }.toTypedArray()
    return value to remaining
}

private fun parsePort(args: Array<String>): Int {
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
    return port
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
          battletech-tui [--map <name|path>]                        hot-seat (both players share this terminal)
          battletech-tui --host [--port N] [--map <name|path>]      host a session (default port $DEFAULT_PORT)
          battletech-tui --join <ip[:port]> --session <id>          join a hosted session
          battletech-tui --server [--port N] [--map <name|path>]    headless dedicated server (both players join remotely)

          --map <name|path>  built-in map id (e.g. "$DEFAULT_MAP_NAME") or a path to a map file;
                              default is "$DEFAULT_MAP_NAME". Invalid with --join (the map comes from the host).
        """.trimIndent(),
    )
    kotlin.system.exitProcess(2)
}

/**
 * Resolves a `--map` name (or [DEFAULT_MAP_NAME] when absent) to a [battletech.tactical.model.GameMap],
 * mirroring the arg-error exit style: on [MapLoadException], print the message to stderr and exit(2)
 * with no stack trace.
 */
private fun resolveMapOrExit(mapName: String?) = try {
    resolveMap(mapName ?: DEFAULT_MAP_NAME)
} catch (e: MapLoadException) {
    System.err.println(e.message)
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
        is Mode.Local -> {
            val map = resolveMapOrExit(mode.mapName)
            TuiApp(initialGameState = GameStateFactory().sampleGameState(map)).run()
        }

        is Mode.Host -> {
            val map = resolveMapOrExit(mode.mapName)
            val server = GameServer.host(GameStateFactory().sampleGameState(map), mode.port)
            server.start()
            println("Session ID: ${server.sessionId} — listening on port ${server.boundPort}")
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

        is Mode.Server -> runHeadlessServer(mode.port, resolveMapOrExit(mode.mapName))
    }
}

/**
 * Headless dedicated server: no [TuiApp], no Mordant terminal. Both players connect
 * remotely via `--join`. Runs until Ctrl-C (or another SIGTERM), printing every game
 * event to stdout as it happens; the process stays up after [battletech.tactical.session.MatchEnded].
 */
private fun runHeadlessServer(port: Int, map: GameMap) {
    val server = GameServer.host(
        GameStateFactory().sampleGameState(map),
        port,
        remoteSeats = setOf(PlayerId.PLAYER_1, PlayerId.PLAYER_2),
    )

    val printer = GameEventPrinter(System.out)
    // Replay the seeded notices before subscribing so the printer sees the whole log from the
    // start without racing the accept loop — see GameServer.host's KDoc for why this is safe.
    server.gameLog.snapshot().forEach { entry -> printer.print(entry.event, server.gameState, entry.turn) }
    server.subscribe { event ->
        printer.print(event, server.gameState, server.turnState.turnNumber)
    }

    server.start()
    println("Session ID: ${server.sessionId} — listening on port ${server.boundPort}")

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

    /**
     * [gameState] is the authoritative, unfiltered state a headless server console
     * legitimately has (see the class doc) — there is no single "viewer" to project
     * for here, so it's revealed in full via [projectFor] rather than redacted for
     * an arbitrary seat. [GameLogFormatter] itself takes the same [PlayerGameState]
     * shape every other consumer (the in-game LOG panel) does.
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
