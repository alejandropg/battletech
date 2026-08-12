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
import battletech.tui.screen.TuiTheme
import battletech.tui.view.GameLogFormatter
import java.io.IOException
import java.util.concurrent.CountDownLatch

/** Default TCP port for `--host`/`--join`/`--server` when `--port`/an explicit port is not supplied. */
internal const val DEFAULT_PORT: Int = 2470

/** Built-in map name used when `--map` is not supplied. */
internal const val DEFAULT_MAP_NAME: String = "default"

/**
 * The four ways the TUI can be launched, resolved from CLI args by [parseArgs].
 * [Local] is today's hot-seat behavior (both players share one terminal).
 * [Host] starts a [GameServer] and seats the local player as [PlayerId.PLAYER_1].
 * [Join] connects to a remote host and seats the local player as whatever seat the server assigns.
 * [Server] starts a headless dedicated server — no TUI — and both players connect via [Join].
 */
internal sealed interface Mode {
    data class Local(val mapName: String? = null, val theme: TuiTheme? = null) : Mode
    data class Host(val port: Int = DEFAULT_PORT, val mapName: String? = null, val theme: TuiTheme? = null) : Mode
    data class Join(val host: String, val port: Int = DEFAULT_PORT, val sessionId: String, val theme: TuiTheme? = null) : Mode
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
 * - `--host [--port N] [--map <name|path>] [--theme <name>]`: [Mode.Host]
 * - `--join <ip[:port]> --session <id> [--theme <name>]`: [Mode.Join]
 * - `--server [--port N] [--map <name|path>]`: [Mode.Server]
 * - `[--map <name|path>]` may additionally appear anywhere for hot-seat/host/server (invalid with `--join`).
 * - `[--theme <name>]` may additionally appear anywhere for hot-seat/host/join (invalid with `--server`).
 */
internal fun parseArgs(args: Array<String>): Mode {
    val (mapName, afterMap) = extractMapArg(args)
    val (themeFlag, rest) = extractThemeArg(afterMap)
    val theme = themeFlag?.let(::parseTheme)

    if (rest.isEmpty()) return Mode.Local(mapName = mapName, theme = theme)

    return when (rest[0]) {
        "--host" -> Mode.Host(parsePort(rest), mapName, theme)
        "--join" -> {
            if (mapName != null) throw ArgsException("--map cannot be combined with --join")
            parseJoin(rest, theme)
        }
        "--server" -> {
            if (theme != null) throw ArgsException("--theme cannot be combined with --server")
            Mode.Server(parsePort(rest), mapName)
        }
        else -> throw ArgsException("Unknown argument: ${rest[0]}")
    }
}

/**
 * Pulls the first `<flag> <value>` pair out of [args], wherever it appears, returning the value
 * (or null if absent) alongside the remaining args in their original relative order. Throws
 * [ArgsException] if [flag] is present with no following value.
 *
 * Only the FIRST occurrence is extracted — a second [flag] is left in the residual args, where it
 * falls through to "Unknown argument: <flag>" the same way any other unrecognized argument does.
 * That is deliberate: every extracted flag shares this one duplicate-flag behavior instead of each
 * growing its own bespoke message.
 */
private fun extractFlagArg(args: Array<String>, flag: String, missingValueMessage: String): Pair<String?, Array<String>> {
    val index = args.indexOf(flag)
    if (index == -1) return null to args

    val value = args.getOrNull(index + 1) ?: throw ArgsException(missingValueMessage)
    val remaining = args.filterIndexed { i, _ -> i != index && i != index + 1 }.toTypedArray()
    return value to remaining
}

private fun extractMapArg(args: Array<String>): Pair<String?, Array<String>> =
    extractFlagArg(args, "--map", "--map requires a value")

private fun extractThemeArg(args: Array<String>): Pair<String?, Array<String>> =
    extractFlagArg(args, "--theme", "--theme requires a value")

private fun parseTheme(value: String): TuiTheme =
    TuiTheme.fromFlag(value) ?: throw ArgsException(
        "Unknown theme: $value (expected dark, light, dark-256, light-256, dark-16 or light-16)",
    )

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

private fun parseJoin(args: Array<String>, theme: TuiTheme?): Mode.Join {
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

    return Mode.Join(host = host, port = port, sessionId = id, theme = theme)
}

private fun printUsageAndExit(message: String): Nothing {
    System.err.println(message)
    System.err.println(
        """
        Usage:
          battletech-tui [--map <name|path>] [--theme <name>]                        hot-seat (both players share this terminal)
          battletech-tui --host [--port N] [--map <name|path>] [--theme <name>]      host a session (default port $DEFAULT_PORT)
          battletech-tui --join <ip[:port]> --session <id> [--theme <name>]          join a hosted session
          battletech-tui --server [--port N] [--map <name|path>]                     headless dedicated server (both players join remotely)

          --map <name|path>  built-in map name (e.g. "battletech-classic") or an existing
                              map-file path; default is "$DEFAULT_MAP_NAME". Invalid with --join
                              (the map comes from the host).
          --theme <name>     dark|light|dark-256|light-256|dark-16|light-16; default is chosen from
                              the terminal's detected color support. Invalid with --server.
        """.trimIndent(),
    )
    kotlin.system.exitProcess(2)
}

/**
 * Resolves a `--map` built-in name or existing path (or [DEFAULT_MAP_NAME] when absent) to a
 * [battletech.tactical.model.GameMap], mirroring the arg-error exit style: on [MapLoadException],
 * print the message to stderr and exit(2) with no stack trace.
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
    val mode = try {
        parseArgs(args)
    } catch (e: ArgsException) {
        printUsageAndExit(e.message ?: "Invalid arguments")
    }

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
 * remotely via `--join`. Runs until Ctrl-C (or another SIGTERM), printing every game
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
