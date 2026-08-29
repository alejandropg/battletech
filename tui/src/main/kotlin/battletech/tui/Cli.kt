package battletech.tui

import battletech.tactical.model.game.DEFAULT_GAME_NAME
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal

/** Default TCP port for `host`/`join`/`server` when `--port`/an explicit port is not supplied. */
internal const val DEFAULT_PORT: Int = 2470

/**
 * The four ways the TUI can be launched, resolved from CLI args by [parseArgs].
 * [Local] is today's hot-seat behavior (both players share one terminal).
 * [Host] starts a [battletech.network.server.GameServer] and seats the local player as
 * [battletech.tactical.model.PlayerId.PLAYER_1].
 * [Join] connects to a remote host and seats the local player as whatever seat the server assigns.
 * [Server] starts a headless dedicated server — no TUI — and both players connect via [Join].
 */
internal sealed interface Mode {
    data class Local(
        val gameName: String? = null,
        val mapPaths: List<String> = emptyList(),
        val themeName: String? = null,
    ) : Mode

    data class Host(
        val port: Int = DEFAULT_PORT,
        val gameName: String? = null,
        val mapPaths: List<String> = emptyList(),
        val themeName: String? = null,
    ) : Mode

    data class Join(val host: String, val port: Int = DEFAULT_PORT, val sessionId: String, val themeName: String? = null) : Mode

    data class Server(
        val port: Int = DEFAULT_PORT,
        val gameName: String? = null,
        val mapPaths: List<String> = emptyList(),
    ) : Mode
}

private fun ParameterHolder.themeOption() =
    option(
        "--theme",
        metavar = "<name|path>",
        help = "Built-in theme name or theme-file path; default is chosen from the terminal's detected color support",
    )

private fun ParameterHolder.gameOption() =
    option(
        "--game",
        metavar = "<name|path>",
        help = "Built-in game name or game-file path; default is \"$DEFAULT_GAME_NAME\"",
    )

private fun ParameterHolder.mapOptions() =
    option(
        "--map",
        metavar = "<path>",
        help = "External map file to register by filename; may be repeated",
    ).multiple()

private fun ParameterHolder.portOption() =
    option("--port", help = "TCP port to listen on (default $DEFAULT_PORT)").int().default(DEFAULT_PORT)

/**
 * Root command: hot-seat when invoked bare, or dispatches to [HostCommand]/[JoinCommand]/[ServerCommand].
 * `--game`/`--map`/`--theme` live here (not only on the subcommands) so the bare hot-seat form keeps taking
 * them directly — see [parseArgs]'s KDoc for why that shape was chosen over a `hotseat` subcommand.
 *
 * If these options are given ahead of a subcommand, e.g. `battletech-tui --game x host`, they
 * would silently apply to THIS command while `host` uses its own (unset) copies; [run] catches
 * that and fails loudly instead of silently dropping the flag.
 *
 * [cliTerminal]/[cliExit] are only set on the root: [Context.Builder]'s `terminal`/`exitProcess`
 * both default to `parent?.terminal`/`parent?.exitProcess` (the same reason Clikt's own
 * `installMordant` early-returns once a parent has installed one), so every subcommand inherits
 * them for free. Named `cliTerminal`/`cliExit` rather than `terminal`/`exit` because inside
 * `context { }` the receiver is [Context.Builder], whose `terminal` is an extension property —
 * a same-named constructor parameter would shadow it.
 */
private class BattletechTui(
    private val emit: (Mode) -> Unit,
    cliTerminal: Terminal,
    cliExit: (Int) -> Unit,
) : CliktCommand(name = "battletech-tui") {
    init {
        context {
            terminal = cliTerminal
            exitProcess = cliExit
        }
    }

    override fun help(context: Context): String =
        "BattleTech TUI. With no subcommand: hot-seat, both players share this terminal."

    override val invokeWithoutSubcommand: Boolean = true

    private val gameName by gameOption()
    private val mapPaths by mapOptions()
    private val themeName by themeOption()

    override fun run() {
        val sub = currentContext.invokedSubcommand
        if (sub == null) {
            emit(Mode.Local(gameName = gameName, mapPaths = mapPaths, themeName = themeName))
            return
        }
        val misplaced = listOfNotNull(
            "--game".takeIf { gameName != null },
            "--map".takeIf { mapPaths.isNotEmpty() },
            "--theme".takeIf { themeName != null },
        )
        if (misplaced.isNotEmpty()) {
            throw UsageError("${misplaced.joinToString(" and ")} must come after '${sub.commandName}'")
        }
    }
}

private class HostCommand(private val emit: (Mode) -> Unit) : CliktCommand(name = "host") {
    override fun help(context: Context): String = "Host a session; other players connect with 'join'."

    private val port by portOption()
    private val gameName by gameOption()
    private val mapPaths by mapOptions()
    private val themeName by themeOption()

    override fun run() {
        emit(Mode.Host(port = port, gameName = gameName, mapPaths = mapPaths, themeName = themeName))
    }
}

/** Host and port split out of `join`'s single positional `ADDRESS` argument. */
private data class Endpoint(val host: String, val port: Int)

private class JoinCommand(private val emit: (Mode) -> Unit) : CliktCommand(name = "join") {
    override fun help(context: Context): String = "Join a hosted session. The map comes from the host."

    // fail() belongs to the convert{} receiver (ArgumentTransformContext), so the host:port split
    // stays inline rather than being extracted to a top-level helper. Bracketed IPv6 addresses
    // (e.g. "[::1]:2470") are not supported — true of the parser this replaces too.
    private val endpoint by argument(
        name = "ADDRESS",
        help = "Host IP or name, optionally :port (default $DEFAULT_PORT)",
    ).convert { raw ->
        val colon = raw.indexOf(':')
        if (colon == -1) {
            if (raw.isEmpty()) fail("malformed host: $raw")
            Endpoint(raw, DEFAULT_PORT)
        } else {
            val host = raw.substring(0, colon)
            if (host.isEmpty()) fail("malformed host: $raw")
            val port = raw.substring(colon + 1).toIntOrNull() ?: fail("malformed port: $raw")
            Endpoint(host, port)
        }
    }

    private val sessionId by option(
        "--session",
        metavar = "ID",
        help = "Session ID printed by the host on startup",
    ).required()
    private val themeName by themeOption()

    override fun run() {
        emit(Mode.Join(host = endpoint.host, port = endpoint.port, sessionId = sessionId, themeName = themeName))
    }
}

private class ServerCommand(private val emit: (Mode) -> Unit) : CliktCommand(name = "server") {
    override fun help(context: Context): String = "Headless dedicated server; both players connect with 'join'."

    private val port by portOption()
    private val gameName by gameOption()
    private val mapPaths by mapOptions()

    override fun run() {
        emit(Mode.Server(port = port, gameName = gameName, mapPaths = mapPaths))
    }
}

/**
 * Parses [args] into a [Mode] via Clikt's standard [CliktCommand.main] entry point: returns the
 * resolved [Mode], or the process exits — with `--help`/usage text on the correct stream and
 * Clikt's own exit code — without returning at all. `main()`'s body is therefore a single call:
 * these commands only ever *name* a [Mode] via [emit], they never branch on one, so `main()`
 * stays the only place that knows which mode ran.
 *
 * [terminal]/[exit] exist only so tests can drive this without touching the real stdout/stderr or
 * the real JVM `exitProcess` (which [CliktCommand.main] calls for real on the success-exits-early
 * paths — `--help`, any usage error — and would otherwise kill the test run); production callers
 * use the defaults. This is Clikt's own documented seam for testing a `main()`-shaped command:
 * a [Terminal] backed by [com.github.ajalt.mordant.terminal.TerminalRecorder], and a
 * [Context.Builder.exitProcess] override.
 *
 * The command tree is built fresh on every call — Clikt commands hold parsed option state and
 * cannot be reused across [CliktCommand.main] calls — which is also what keeps this function
 * re-entrant for tests.
 *
 * Syntax:
 * - (no args), or `[--game <name|path>] [--map <path>]... [--theme <name|path>]`: [Mode.Local]
 * - `host [--port N] [--game <name|path>] [--map <path>]... [--theme <name|path>]`: [Mode.Host]
 * - `join <ip[:port]> --session <id> [--theme <name|path>]`: [Mode.Join]
 * - `server [--port N] [--game <name|path>] [--map <path>]...`: [Mode.Server]
 *
 * The game/map/theme options are declared on both the root and the relevant subcommands (rather than only
 * the root) so `host --help`/`join --help`/`server --help` each show exactly the options that
 * command accepts — `join --help` never mentions `--game`/`--map` and `server --help` never mentions
 * `--theme`. That structural guarantee is what replaces the old hand-written "--map cannot be
 * combined with --join" / "--theme cannot be combined with --server" checks.
 */
internal fun parseArgs(
    args: Array<String>,
    terminal: Terminal = Terminal(ansiLevel = AnsiLevel.NONE),
    exit: (Int) -> Unit = { kotlin.system.exitProcess(it) },
): Mode {
    var resolved: Mode? = null
    val emit: (Mode) -> Unit = { resolved = it }
    val root = BattletechTui(emit, terminal, exit)
        .subcommands(HostCommand(emit), JoinCommand(emit), ServerCommand(emit))
    root.main(args.toList())
    return checkNotNull(resolved) { "no Mode was resolved from ${args.toList()}" }
}
