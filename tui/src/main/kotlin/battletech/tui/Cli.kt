package battletech.tui

import battletech.tui.screen.TuiTheme
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
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal

/** Default TCP port for `host`/`join`/`serve` when `--port`/an explicit port is not supplied. */
internal const val DEFAULT_PORT: Int = 2470

/** Built-in map name used when `--map` is not supplied. */
internal const val DEFAULT_MAP_NAME: String = "default"

/**
 * The four ways the TUI can be launched, resolved from CLI args by [parseArgs].
 * [Local] is today's hot-seat behavior (both players share one terminal).
 * [Host] starts a [battletech.network.server.GameServer] and seats the local player as
 * [battletech.tactical.model.PlayerId.PLAYER_1].
 * [Join] connects to a remote host and seats the local player as whatever seat the server assigns.
 * [Server] starts a headless dedicated server — no TUI — and both players connect via [Join].
 */
internal sealed interface Mode {
    data class Local(val mapName: String? = null, val theme: TuiTheme? = null) : Mode
    data class Host(val port: Int = DEFAULT_PORT, val mapName: String? = null, val theme: TuiTheme? = null) : Mode
    data class Join(val host: String, val port: Int = DEFAULT_PORT, val sessionId: String, val theme: TuiTheme? = null) : Mode
    data class Server(val port: Int = DEFAULT_PORT, val mapName: String? = null) : Mode
}

private val THEME_CHOICES: Map<String, TuiTheme> = TuiTheme.entries.associateBy { it.flag }

/**
 * `choice()` derives both the metavar and the "invalid choice" message from [THEME_CHOICES], so
 * the six theme names are never hand-typed anywhere in the CLI layer (unlike the old parser's
 * hardcoded "expected dark, light, ..." string).
 */
private fun ParameterHolder.themeOption() =
    option("--theme", help = "Color theme; default is chosen from the terminal's detected color support")
        .choice(THEME_CHOICES)

private fun ParameterHolder.mapOption() =
    option(
        "--map",
        metavar = "<name|path>",
        help = "Built-in map name or map-file path; default is \"$DEFAULT_MAP_NAME\"",
    )

private fun ParameterHolder.portOption() =
    option("--port", help = "TCP port to listen on (default $DEFAULT_PORT)").int().default(DEFAULT_PORT)

/**
 * Root command: hot-seat when invoked bare, or dispatches to [HostCommand]/[JoinCommand]/[ServeCommand].
 * `--map`/`--theme` live here (not only on the subcommands) so the bare hot-seat form keeps taking
 * them directly — see [parseArgs]'s KDoc for why that shape was chosen over a `hotseat` subcommand.
 *
 * If `--map`/`--theme` are given ahead of a subcommand, e.g. `battletech-tui --map x host`, they
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

    private val mapName by mapOption()
    private val theme by themeOption()

    override fun run() {
        val sub = currentContext.invokedSubcommand
        if (sub == null) {
            emit(Mode.Local(mapName = mapName, theme = theme))
            return
        }
        val misplaced = listOfNotNull("--map".takeIf { mapName != null }, "--theme".takeIf { theme != null })
        if (misplaced.isNotEmpty()) {
            throw UsageError("${misplaced.joinToString(" and ")} must come after '${sub.commandName}'")
        }
    }
}

private class HostCommand(private val emit: (Mode) -> Unit) : CliktCommand(name = "host") {
    override fun help(context: Context): String = "Host a session; other players connect with 'join'."

    private val port by portOption()
    private val mapName by mapOption()
    private val theme by themeOption()

    override fun run() {
        emit(Mode.Host(port = port, mapName = mapName, theme = theme))
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
    private val theme by themeOption()

    override fun run() {
        emit(Mode.Join(host = endpoint.host, port = endpoint.port, sessionId = sessionId, theme = theme))
    }
}

private class ServeCommand(private val emit: (Mode) -> Unit) : CliktCommand(name = "serve") {
    override fun help(context: Context): String = "Headless dedicated server; both players connect with 'join'."

    private val port by portOption()
    private val mapName by mapOption()

    override fun run() {
        emit(Mode.Server(port = port, mapName = mapName))
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
 * - (no args), or `[--map <name|path>] [--theme <name>]`: [Mode.Local]
 * - `host [--port N] [--map <name|path>] [--theme <name>]`: [Mode.Host]
 * - `join <ip[:port]> --session <id> [--theme <name>]`: [Mode.Join]
 * - `serve [--port N] [--map <name|path>]`: [Mode.Server]
 *
 * `--map`/`--theme` are declared on both the root and the relevant subcommands (rather than only
 * the root) so `host --help`/`join --help`/`serve --help` each show exactly the options that
 * command accepts — `join --help` never mentions `--map` and `serve --help` never mentions
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
        .subcommands(HostCommand(emit), JoinCommand(emit), ServeCommand(emit))
    root.main(args.toList())
    return checkNotNull(resolved) { "no Mode was resolved from ${args.toList()}" }
}
