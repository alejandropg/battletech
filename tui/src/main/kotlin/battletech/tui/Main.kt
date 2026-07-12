package battletech.tui

import battletech.network.client.JoinRejectedException
import battletech.network.client.RemoteGameSession
import battletech.network.server.GameServer
import battletech.network.wire.SessionId
import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.PlayerId
import battletech.tactical.session.BattleSession
import battletech.tactical.session.SessionNotice
import battletech.tactical.session.TurnState
import java.io.IOException

/** Default TCP port for `--host`/`--join` when `--port`/an explicit port is not supplied. */
internal const val DEFAULT_PORT: Int = 2470

/**
 * The three ways the TUI can be launched, resolved from CLI args by [parseArgs].
 * [Local] is today's hot-seat behavior (both players share one terminal).
 * [Host] starts a [GameServer] and seats the local player as [PlayerId.PLAYER_1].
 * [Join] connects to a remote host and seats the local player as [PlayerId.PLAYER_2].
 */
internal sealed interface Mode {
    data object Local : Mode
    data class Host(val port: Int = DEFAULT_PORT) : Mode
    data class Join(val host: String, val port: Int = DEFAULT_PORT, val sessionId: String) : Mode
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
 */
internal fun parseArgs(args: Array<String>): Mode {
    if (args.isEmpty()) return Mode.Local

    return when (args[0]) {
        "--host" -> parseHost(args)
        "--join" -> parseJoin(args)
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
                    localPlayer = PlayerId.PLAYER_2,
                ).run()
            }
        }
    }
}
