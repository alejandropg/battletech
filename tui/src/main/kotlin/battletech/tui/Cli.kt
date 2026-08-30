package battletech.tui

import battletech.tactical.model.game.DEFAULT_GAME_NAME
import battletech.tactical.model.game.builtInGameNames
import battletech.tactical.model.map.GameMapLoader
import battletech.tactical.model.mech.MechLoadException
import battletech.tactical.model.mech.builtInMechCollectionNames
import battletech.tactical.model.mech.mechCollectionVariants
import battletech.tui.screen.ThemeLoader
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.io.path.Path
import kotlin.io.path.exists

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
        val mechPaths: List<String> = emptyList(),
        val themeName: String? = null,
    ) : Mode

    data class Host(
        val port: Int = DEFAULT_PORT,
        val gameName: String? = null,
        val mapPaths: List<String> = emptyList(),
        val mechPaths: List<String> = emptyList(),
        val themeName: String? = null,
    ) : Mode

    data class Join(val host: String, val port: Int = DEFAULT_PORT, val sessionId: String, val themeName: String? = null) : Mode

    data class Server(
        val port: Int = DEFAULT_PORT,
        val gameName: String? = null,
        val mapPaths: List<String> = emptyList(),
        val mechPaths: List<String> = emptyList(),
    ) : Mode
}

/**
 * These four are declared `eager = true` not because they behave eagerly themselves, but because
 * the `--list-*` flags' own eager [CliktCommand.exitIfListingRequested] check needs to read their
 * values — Clikt finalizes eager options in a separate, earlier pass than non-eager ones (see that
 * function's KDoc), so a non-eager option's value is unavailable — reading it throws
 * [com.github.ajalt.clikt.parameters.internal.LateinitException] — from inside an eager option's
 * `validate { }`. Marking these four eager too puts them in that same early pass.
 */
private fun ParameterHolder.themeOption() =
    option(
        "--theme",
        metavar = "<name|path>",
        eager = true,
        help = "Built-in theme name or theme-file path; default is chosen from the terminal's detected color support",
    )

private fun ParameterHolder.gameOption() =
    option(
        "--game",
        metavar = "<name|path>",
        eager = true,
        help = "Built-in game name or game-file path; default is \"$DEFAULT_GAME_NAME\"",
    )

private fun ParameterHolder.mapOptions() =
    option(
        "--map",
        metavar = "<path>",
        eager = true,
        help = "External map file to register by filename; may be repeated",
    ).multiple()

private fun ParameterHolder.mechOptions() =
    option(
        "--mech",
        metavar = "<path>",
        eager = true,
        help = "External mech-model collection file; may be repeated",
    ).multiple()

private fun ParameterHolder.portOption() =
    option("--port", help = "TCP port to listen on (default $DEFAULT_PORT)").int().default(DEFAULT_PORT)

private fun ParameterHolder.listMapsFlag() =
    option("--list-maps", eager = true, help = "List built-in and registered maps, then exit").flag()

private fun ParameterHolder.listMechsFlag() =
    option("--list-mechs", eager = true, help = "List built-in and registered mech collections, then exit").flag()

private fun ParameterHolder.listGamesFlag() =
    option("--list-games", eager = true, help = "List built-in and registered games, then exit").flag()

private fun ParameterHolder.listThemesFlag() =
    option("--list-themes", eager = true, help = "List built-in and registered themes, then exit").flag()

/** Derives the display name an external asset is registered under: its filename minus `.json`. */
private fun externalAssetName(path: String): String =
    Path(path).fileName?.toString().orEmpty().removeSuffix(".json")

private fun renderFlatSection(title: String, builtIns: List<String>, externalNames: List<String>): String {
    val lines = builtIns.map { "  $it" } + externalNames.map { "  $it (external)" }
    return (listOf("$title:") + lines).joinToString("\n")
}

private fun renderMapsSection(externalPaths: List<String>): String =
    renderFlatSection("Maps", GameMapLoader().builtInNames(), externalPaths.map(::externalAssetName))

private fun renderGamesSection(gameName: String?): String {
    val externalName = gameName?.takeIf { Path(it).exists() }?.let(::externalAssetName)
    return renderFlatSection("Games", builtInGameNames(), listOfNotNull(externalName))
}

private fun renderThemesSection(themeName: String?): String {
    val externalName = themeName?.takeIf { Path(it).exists() }?.let(::externalAssetName)
    return renderFlatSection("Themes", ThemeLoader().builtInNames(), listOfNotNull(externalName))
}

/**
 * Unlike maps/games/themes, a mech collection's listing requires opening the file to enumerate its
 * variants, so a bad external path can genuinely fail here — routed through [PrintMessage] (not a
 * raw `exitProcess`) so it still exits via the same injected, test-safe mechanism as every other
 * Clikt-side error in this file.
 */
private fun renderMechsSection(externalPaths: List<String>): String {
    val builtInLines = builtInMechCollectionNames().flatMap { name ->
        listOf("  $name:") + mechCollectionVariants(name).map { "    $it" }
    }
    val externalLines = externalPaths.flatMap { p ->
        val name = externalAssetName(p)
        val variants = try {
            mechCollectionVariants(Path(p))
        } catch (e: MechLoadException) {
            throw PrintMessage(e.message.orEmpty(), statusCode = 2, printError = true)
        }
        listOf("  $name (external):") + variants.map { "    $it" }
    }
    return (listOf("Mechs:") + builtInLines + externalLines).joinToString("\n")
}

/**
 * Prints [sections] joined with a blank line and exits 0, or does nothing if [sections] is empty.
 *
 * Called from every `--list-*` flag's own `validate { }` (not just one of them): Clikt only calls
 * an eager option's `validate` when that specific option was actually given on the command line, so
 * whichever list flag(s) the user typed are the ones whose `validate` fires. Calling this same
 * function from each is safe and not redundant — it always rebuilds the full combined [sections]
 * list from every list flag's current (already-finalized) value, and the first call to run throws
 * [ProgramResult], which unwinds before any other invoked flag's `validate` gets a turn.
 */
private fun CliktCommand.exitIfAnyListingRequested(sections: List<String>) {
    if (sections.isEmpty()) return
    echo(sections.joinToString("\n\n"))
    throw ProgramResult(0)
}

/**
 * Root command: hot-seat when invoked bare, or dispatches to [HostCommand]/[JoinCommand]/[ServerCommand].
 * `--game`/`--map`/`--mech`/`--theme` live here (not only on the subcommands) so the bare hot-seat form keeps taking
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
    private val mechPaths by mechOptions()
    private val themeName by themeOption()
    private val listMaps by listMapsFlag().validate { exitIfListingRequested() }
    private val listMechs by listMechsFlag().validate { exitIfListingRequested() }
    private val listGames by listGamesFlag().validate { exitIfListingRequested() }
    private val listThemes by listThemesFlag().validate { exitIfListingRequested() }

    private fun exitIfListingRequested(): Unit = exitIfAnyListingRequested(
        buildList {
            if (listMaps) add(renderMapsSection(mapPaths))
            if (listMechs) add(renderMechsSection(mechPaths))
            if (listGames) add(renderGamesSection(gameName))
            if (listThemes) add(renderThemesSection(themeName))
        },
    )

    override fun run() {
        val sub = currentContext.invokedSubcommand
        if (sub == null) {
            emit(Mode.Local(gameName = gameName, mapPaths = mapPaths, mechPaths = mechPaths, themeName = themeName))
            return
        }
        val misplaced = listOfNotNull(
            "--game".takeIf { gameName != null },
            "--map".takeIf { mapPaths.isNotEmpty() },
            "--mech".takeIf { mechPaths.isNotEmpty() },
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
    private val mechPaths by mechOptions()
    private val themeName by themeOption()
    private val listMaps by listMapsFlag().validate { exitIfListingRequested() }
    private val listMechs by listMechsFlag().validate { exitIfListingRequested() }
    private val listGames by listGamesFlag().validate { exitIfListingRequested() }
    private val listThemes by listThemesFlag().validate { exitIfListingRequested() }

    private fun exitIfListingRequested(): Unit = exitIfAnyListingRequested(
        buildList {
            if (listMaps) add(renderMapsSection(mapPaths))
            if (listMechs) add(renderMechsSection(mechPaths))
            if (listGames) add(renderGamesSection(gameName))
            if (listThemes) add(renderThemesSection(themeName))
        },
    )

    override fun run() {
        emit(
            Mode.Host(
                port = port,
                gameName = gameName,
                mapPaths = mapPaths,
                mechPaths = mechPaths,
                themeName = themeName,
            ),
        )
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
    private val listThemes by listThemesFlag().validate { exitIfListingRequested() }

    private fun exitIfListingRequested(): Unit = exitIfAnyListingRequested(
        buildList {
            if (listThemes) add(renderThemesSection(themeName))
        },
    )

    override fun run() {
        emit(Mode.Join(host = endpoint.host, port = endpoint.port, sessionId = sessionId, themeName = themeName))
    }
}

private class ServerCommand(private val emit: (Mode) -> Unit) : CliktCommand(name = "server") {
    override fun help(context: Context): String = "Headless dedicated server; both players connect with 'join'."

    private val port by portOption()
    private val gameName by gameOption()
    private val mapPaths by mapOptions()
    private val mechPaths by mechOptions()
    private val listMaps by listMapsFlag().validate { exitIfListingRequested() }
    private val listMechs by listMechsFlag().validate { exitIfListingRequested() }
    private val listGames by listGamesFlag().validate { exitIfListingRequested() }

    private fun exitIfListingRequested(): Unit = exitIfAnyListingRequested(
        buildList {
            if (listMaps) add(renderMapsSection(mapPaths))
            if (listMechs) add(renderMechsSection(mechPaths))
            if (listGames) add(renderGamesSection(gameName))
        },
    )

    override fun run() {
        emit(Mode.Server(port = port, gameName = gameName, mapPaths = mapPaths, mechPaths = mechPaths))
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
 * - (no args), or `[--game <name|path>] [--map <path>]... [--mech <path>]... [--theme <name|path>]`: [Mode.Local]
 * - `host [--port N] [--game <name|path>] [--map <path>]... [--mech <path>]... [--theme <name|path>]`: [Mode.Host]
 * - `join <ip[:port]> --session <id> [--theme <name|path>]`: [Mode.Join]
 * - `server [--port N] [--game <name|path>] [--map <path>]... [--mech <path>]...`: [Mode.Server]
 *
 * The game/map/mech/theme options are declared on both the root and the relevant subcommands (rather than only
 * the root) so `host --help`/`join --help`/`server --help` each show exactly the options that
 * command accepts — `join --help` never mentions `--game`/`--map`/`--mech` and `server --help` never mentions
 * `--theme`. That structural guarantee is what replaces the old hand-written "--map cannot be
 * combined with --join" / "--theme cannot be combined with --server" checks.
 *
 * Each of `--game`/`--map`/`--mech`/`--theme` has a matching `--list-games`/`--list-maps`/`--list-mechs`/
 * `--list-themes` flag (declared wherever its counterpart is), following the same per-command availability
 * rule. These are eager, like `--help`: they fire and exit 0 before any other option is validated — even a
 * missing required `--session`/`ADDRESS` on `join` — and any combination of them prints its sections together.
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
