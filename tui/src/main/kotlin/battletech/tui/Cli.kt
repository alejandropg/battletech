package battletech.tui

import battletech.tactical.io.CatalogEntry
import battletech.tactical.io.NestedCatalogEntry
import battletech.tactical.model.content.ContentCatalog
import battletech.tactical.model.map.DEFAULT_MAP_NAME
import battletech.tactical.model.map.MapLoadException
import battletech.tactical.model.mech.MechLoadException
import battletech.tactical.model.unit.DEFAULT_UNITS_NAME
import battletech.tactical.model.unit.UnitCollectionListing
import battletech.tactical.model.unit.UnitLoadException
import battletech.tactical.unit.MechModel
import battletech.tui.screen.ThemeLoader
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
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

/** Board and unit-collection selection shared by [Mode.HotSeat], [Mode.Host], and [Mode.Server]. */
internal data class Setup(
    val mapName: String = DEFAULT_MAP_NAME,
    val unitsName: String = DEFAULT_UNITS_NAME,
)

/**
 * The four ways the TUI can be launched, resolved from CLI args by [parseArgs]. [HotSeat] is
 * today's hot-seat behavior (both players share one terminal). [Host] starts a
 * [battletech.network.server.GameServer] and seats the local player as
 * [battletech.tactical.model.PlayerId.PLAYER_1]. [Join] connects to a remote host and seats the
 * local player as whatever seat the server assigns — the board and roster come from the host, so
 * it carries neither a [Setup] nor a theme. [Server] starts a headless dedicated server — no TUI —
 * and both players connect via [Join].
 */
internal sealed interface Mode {
    data class HotSeat(val setup: Setup = Setup()) : Mode
    data class Host(val port: Int = DEFAULT_PORT, val setup: Setup = Setup()) : Mode
    data class Join(val host: String, val port: Int = DEFAULT_PORT, val sessionId: String) : Mode
    data class Server(val port: Int = DEFAULT_PORT, val setup: Setup = Setup()) : Mode

    /** Bare invocation, no subcommand: opens the interactive setup screen (D1). */
    data object Interactive : Mode
}

/** What one `parseArgs` call resolved: every root-level registration plus the dispatched [Mode]. */
internal data class Launch(
    val mapPaths: List<String>,
    val mechPaths: List<String>,
    val unitPaths: List<String>,
    val themeName: String?,
    val mode: Mode,
)

private fun ParameterHolder.themeOption() =
    option(
        "--theme",
        metavar = "<name|path>",
        eager = true,
        help = "Built-in theme name or theme-file path; default is chosen from the terminal's detected color support. " +
            "No effect on 'server', which is headless.",
    )

private fun ParameterHolder.addMapOption() =
    option(
        "--add-map",
        metavar = "<path>",
        eager = true,
        help = "Register an external map file, by filename; may be repeated",
    ).multiple()

private fun ParameterHolder.addMechOption() =
    option(
        "--add-mech",
        metavar = "<path>",
        eager = true,
        help = "Register an external mech-model collection file; may be repeated",
    ).multiple()

private fun ParameterHolder.addUnitOption() =
    option(
        "--add-unit",
        metavar = "<path>",
        eager = true,
        help = "Register an external unit-collection file, by filename; may be repeated",
    ).multiple()

private fun ParameterHolder.mapOption() =
    option(
        "--map",
        metavar = "<name>",
        help = "Board to play on, from the registered maps; default is \"$DEFAULT_MAP_NAME\"",
    ).default(DEFAULT_MAP_NAME)

private fun ParameterHolder.unitOption() =
    option(
        "--unit",
        metavar = "<name>",
        help = "Unit collection to play, from the registered units; default is \"$DEFAULT_UNITS_NAME\"",
    ).default(DEFAULT_UNITS_NAME)

private fun ParameterHolder.portOption() =
    option("--port", help = "TCP port to listen on (default $DEFAULT_PORT)").int().default(DEFAULT_PORT)

private fun ParameterHolder.listMapsFlag() =
    option("--list-maps", eager = true, help = "List built-in and registered maps, then exit").flag()

private fun ParameterHolder.listMechsFlag() =
    option("--list-mechs", eager = true, help = "List built-in and registered mech collections, then exit").flag()

private fun ParameterHolder.listUnitsFlag() =
    option("--list-units", eager = true, help = "List built-in and registered unit collections, then exit").flag()

private fun ParameterHolder.listThemesFlag() =
    option("--list-themes", eager = true, help = "List built-in and registered themes, then exit").flag()

/** Derives the display name an external theme is registered under: its filename minus `.json`. */
private fun externalThemeName(path: String): String = Path(path).fileName?.toString().orEmpty().removeSuffix(".json")

private fun renderFlatSection(title: String, entries: List<CatalogEntry>): String {
    val lines = entries.map { e -> if (e.external) "  ${e.name} (external)" else "  ${e.name}" }
    return (listOf("$title:") + lines).joinToString("\n")
}

private fun renderNestedSection(title: String, entries: List<NestedCatalogEntry>): String {
    val lines = entries.flatMap { e ->
        val header = if (e.external) "  ${e.name} (external):" else "  ${e.name}:"
        listOf(header) + e.items.map { "    $it" }
    }
    return (listOf("$title:") + lines).joinToString("\n")
}

private fun renderMechsSection(
    entries: List<NestedCatalogEntry>,
    findMech: (String) -> MechModel?,
): String {
    val modelsByCollection = entries.map { entry ->
        entry to entry.items.map { variant ->
            checkNotNull(findMech(variant)) { "Mech variant '$variant' is missing from the catalog" }
        }
    }
    val models = modelsByCollection.flatMap { it.second }
    val variantWidth = maxOf("variant".length, models.maxOfOrNull { it.variant.length } ?: 0)
    val nameWidth = maxOf("name".length, models.maxOfOrNull { it.name.length } ?: 0)
    val tonnageWidth = maxOf("tonnage".length, models.maxOfOrNull { it.tonnage.toString().length } ?: 0)
    val walkingWidth = maxOf("walking".length, models.maxOfOrNull { it.walkingMP.toString().length } ?: 0)
    val runningWidth = maxOf("running".length, models.maxOfOrNull { it.runningMP.toString().length } ?: 0)
    val jumpingWidth = maxOf("jumping".length, models.maxOfOrNull { it.jumpMP.toString().length } ?: 0)

    val header = listOf(
        "variant".padEnd(variantWidth),
        "name".padEnd(nameWidth),
        "tonnage".padEnd(tonnageWidth),
        "walking".padStart(walkingWidth),
        "running".padStart(runningWidth),
        "jumping".padStart(jumpingWidth),
    ).joinToString(" ")
    val lines = mutableListOf("Mechs:", "    $header")
    for ((entry, collectionModels) in modelsByCollection) {
        val collectionHeader = if (entry.external) "  ${entry.name} (external):" else "  ${entry.name}:"
        lines += collectionHeader
        lines += collectionModels.map { model ->
            val jumping = model.jumpMP.takeIf { it > 0 }?.toString()?.padStart(jumpingWidth)
                ?: "".padStart(jumpingWidth)
            "    " + listOf(
                model.variant.padEnd(variantWidth),
                model.name.padEnd(nameWidth),
                model.tonnage.toString().padStart(tonnageWidth),
                model.walkingMP.toString().padStart(walkingWidth),
                model.runningMP.toString().padStart(runningWidth),
                jumping,
            ).joinToString(" ")
        }
    }
    return lines.joinToString("\n")
}

private fun renderUnitsSection(entries: List<UnitCollectionListing>): String {
    val units = entries.flatMap { it.units }
    val idWidth = maxOf("id".length, units.maxOfOrNull { it.id.length } ?: 0)
    val playerWidth = maxOf("player".length, units.maxOfOrNull { it.player.toString().length } ?: 0)
    val variantWidth = maxOf("variant".length, units.maxOfOrNull { it.variant.length } ?: 0)
    val gunneryWidth = maxOf("gunnery".length, units.maxOfOrNull { it.gunnery.toString().length } ?: 0)
    val pilotingWidth = maxOf("piloting".length, units.maxOfOrNull { it.piloting.toString().length } ?: 0)
    val colWidth = maxOf("col".length, units.maxOfOrNull { it.col.toString().length } ?: 0)
    val rowWidth = maxOf("row".length, units.maxOfOrNull { it.row.toString().length } ?: 0)
    val facingWidth = maxOf("facing".length, units.maxOfOrNull { it.facing.toString().length } ?: 0)

    val header = listOf(
        "id".padEnd(idWidth),
        "player".padStart(playerWidth),
        "variant".padEnd(variantWidth),
        "gunnery".padStart(gunneryWidth),
        "piloting".padStart(pilotingWidth),
        "col".padStart(colWidth),
        "row".padStart(rowWidth),
        "facing".padEnd(facingWidth),
    ).joinToString(" ")
    val lines = mutableListOf("Units:", "    $header")
    for (entry in entries) {
        val collectionHeader = if (entry.external) "  ${entry.name} (external):" else "  ${entry.name}:"
        lines += collectionHeader
        lines += entry.units.map { unit ->
            "    " + listOf(
                unit.id.padEnd(idWidth),
                unit.player.toString().padStart(playerWidth),
                unit.variant.padEnd(variantWidth),
                unit.gunnery.toString().padStart(gunneryWidth),
                unit.piloting.toString().padStart(pilotingWidth),
                unit.col.toString().padStart(colWidth),
                unit.row.toString().padStart(rowWidth),
                unit.facing.toString().padEnd(facingWidth),
            ).joinToString(" ")
        }
    }
    return lines.joinToString("\n")
}

private fun renderThemesSection(themeName: String?): String {
    val externalName = themeName?.takeIf { Path(it).exists() }?.let(::externalThemeName)
    val builtIns = ThemeLoader().builtInNames()
    val lines = builtIns.map { "  $it" } + listOfNotNull(externalName).map { "  $it (external)" }
    return (listOf("Themes:") + lines).joinToString("\n")
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
 * The root command. Every registration (`--add-map`/`--add-mech`/`--add-unit`), listing
 * (`--list-*`), and `--theme` lives here — valid for every mode, and (per Clikt's token
 * consumption; see [parseArgs]'s KDoc) must precede the subcommand name. This is what deleted the
 * four near-identical option/list blocks the previous per-subcommand tree carried: one
 * [ContentCatalog] now backs every mode's launch and every `--list-*` rendering, so they can
 * never disagree (see [ContentCatalog]'s class KDoc).
 *
 * [cliTerminal]/[cliExit] are only set here: [Context.Builder]'s `terminal`/`exitProcess` both
 * default to `parent?.terminal`/`parent?.exitProcess`, so every subcommand inherits them for
 * free. Named `cliTerminal`/`cliExit` rather than `terminal`/`exit` because inside `context { }`
 * the receiver is [Context.Builder], whose `terminal` is an extension property — a same-named
 * constructor parameter would shadow it.
 */
private class BattletechTui(
    cliTerminal: Terminal,
    cliExit: (Int) -> Unit,
    private val emit: (Mode) -> Unit,
) : CliktCommand(name = "battletech-tui") {
    init {
        context {
            terminal = cliTerminal
            exitProcess = cliExit
        }
    }

    /**
     * Clikt's own built-in "no subcommand given" handling (thrown from `finalizeCommand` when
     * this is left `false`) exits status 0 — it treats bare `battletech-tui` as equivalent to
     * `--help`, not as a usage error. Overriding it to `true` disables that automatic throw and
     * routes control to [run] instead, even with no subcommand, so [run] can [emit]
     * [Mode.Interactive] (D1) rather than being forced to a `--help`-shaped exit.
     */
    override val invokeWithoutSubcommand: Boolean = true

    internal val mapPaths: List<String> by addMapOption()
    internal val mechPaths: List<String> by addMechOption()
    internal val unitPaths: List<String> by addUnitOption()
    internal val themeName: String? by themeOption()
    private val listMaps by listMapsFlag().validate { exitIfListingRequested() }
    private val listMechs by listMechsFlag().validate { exitIfListingRequested() }
    private val listUnits by listUnitsFlag().validate { exitIfListingRequested() }
    private val listThemes by listThemesFlag().validate { exitIfListingRequested() }

    /**
     * Built lazily, once, only when a `--list-*` flag actually needs it — so a bad `--add-*`
     * registration reported here uses the same [PrintMessage] exit path (status 2) every other
     * Clikt-side error in this file uses, rather than a raw `exitProcess`.
     */
    private val content: ContentCatalog by lazy {
        try {
            ContentCatalog.load(mapPaths.map(::Path), mechPaths.map(::Path), unitPaths.map(::Path))
        } catch (e: MapLoadException) {
            throw PrintMessage(e.message.orEmpty(), statusCode = 2, printError = true)
        } catch (e: MechLoadException) {
            throw PrintMessage(e.message.orEmpty(), statusCode = 2, printError = true)
        } catch (e: UnitLoadException) {
            throw PrintMessage(e.message.orEmpty(), statusCode = 2, printError = true)
        }
    }

    private fun exitIfListingRequested(): Unit = exitIfAnyListingRequested(
        buildList {
            if (listMaps) add(renderFlatSection("Maps", content.listing().maps))
            if (listMechs) add(renderMechsSection(content.listing().mechs, content::mech))
            if (listUnits) add(renderUnitsSection(content.unitListings()))
            if (listThemes) add(renderThemesSection(themeName))
        },
    )

    override fun help(context: Context): String =
        "BattleTech TUI. No command: opens the interactive setup screen. Or name one: hot-seat, host, join, server."

    override fun helpEpilog(context: Context): String =
            "Running with no command opens the interactive setup screen, which defines a match and starts it." +
            "\u0085" +
            "Root options (this line's options) must come BEFORE the command name, e.g. " +
            "'${context.commandNameWithParents().joinToString(" ")} --add-map arena.json hot-seat --map arena'." +
            // Mordant collapses ordinary newlines in wrapped text; NEL is its hard line-break marker.
            "\u0085" +
            "Run '${context.commandNameWithParents().joinToString(" ")} <command> --help' for command-specific help."

    /**
     * Runs even with no subcommand (see [invokeWithoutSubcommand]'s KDoc). When a subcommand WAS
     * given, [currentContext.invokedSubcommand][com.github.ajalt.clikt.core.Context.invokedSubcommand]
     * is already populated by the time this runs — parsing builds the whole invocation tree
     * before any finalization/run happens — so this is a no-op and that subcommand's own [run]
     * does the actual work. Otherwise (D1), bare invocation opens the interactive setup screen —
     * every root option (`--add-map`/`--add-mech`/`--add-unit`/`--theme`) and the eager
     * `--list-*` flags still apply exactly as they do for every other mode.
     */
    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            emit(Mode.Interactive)
        }
    }
}

private class HotSeatCommand(private val emit: (Mode) -> Unit) : CliktCommand(name = "hot-seat") {
    override fun help(context: Context): String = "Play hot-seat; both players share this terminal."

    private val mapName by mapOption()
    private val unitsName by unitOption()

    override fun run() {
        emit(Mode.HotSeat(Setup(mapName, unitsName)))
    }
}

private class HostCommand(private val emit: (Mode) -> Unit) : CliktCommand(name = "host") {
    override fun help(context: Context): String = "Host a session; other players connect with 'join'."

    private val port by portOption()
    private val mapName by mapOption()
    private val unitsName by unitOption()

    override fun run() {
        emit(Mode.Host(port = port, setup = Setup(mapName, unitsName)))
    }
}

/** Host and port split out of `join`'s single positional `ADDRESS` argument. */
private data class Endpoint(val host: String, val port: Int)

private class JoinCommand(private val emit: (Mode) -> Unit) : CliktCommand(name = "join") {
    override fun help(context: Context): String = "Join a hosted session. The map and units come from the host."

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

    override fun run() {
        emit(Mode.Join(host = endpoint.host, port = endpoint.port, sessionId = sessionId))
    }
}

private class ServerCommand(private val emit: (Mode) -> Unit) : CliktCommand(name = "server") {
    override fun help(context: Context): String = "Headless dedicated server; both players connect with 'join'."

    private val port by portOption()
    private val mapName by mapOption()
    private val unitsName by unitOption()

    override fun run() {
        emit(Mode.Server(port = port, setup = Setup(mapName, unitsName)))
    }
}

/**
 * Parses [args] into a [Launch] via Clikt's standard [CliktCommand.main] entry point: returns the
 * resolved [Launch], or the process exits — with `--help`/usage text on the correct stream and
 * Clikt's own exit code — without returning at all. There is no default command: an empty [args]
 * is a usage error (Clikt's own `PrintHelpMessage(error = true)`, exit code 1), same as any other
 * missing-subcommand invocation — see the mechanism note below.
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
 * Syntax — root options MUST precede the command name (Clikt's token consumer commits to a
 * subcommand at the first matching token and stops considering root options after that; see
 * `ParserInternals.consumeTokens`), so `battletech-tui hot-seat --add-map x.json` is a usage
 * error, not `battletech-tui --add-map x.json hot-seat`:
 * - `[--add-map <path>]... [--add-mech <path>]... [--add-unit <path>]... [--theme <name|path>] hot-seat [--map <name>] [--unit <name>]`: [Mode.HotSeat]
 * - `... host [--port N] [--map <name>] [--unit <name>]`: [Mode.Host]
 * - `... join <ip[:port]> --session <id>`: [Mode.Join] — no `--map`/`--unit`, no theme; all three come from the host
 * - `... server [--port N] [--map <name>] [--unit <name>]`: [Mode.Server] — `--theme` is accepted at the root but has no effect
 *
 * `--add-map`/`--add-mech`/`--add-unit`/`--theme`/`--list-maps`/`--list-mechs`/`--list-units`/
 * `--list-themes` are declared once, on the root, and are therefore identical across every
 * subcommand; `--map`/`--unit` (selecting FROM what was registered, as opposed to `--add-*`
 * registering it) are declared per-subcommand and absent from `join`, whose board, mechs, and
 * roster all come from the host at join time.
 *
 * The four `--list-*` flags are eager, like `--help`: they fire and exit 0 before any other
 * option is validated — even a missing required `--session`/`ADDRESS` on `join` — and any
 * combination of them prints its sections together, maps then mechs then units then themes.
 */
internal fun parseArgs(
    args: Array<String>,
    terminal: Terminal = Terminal(ansiLevel = AnsiLevel.NONE),
    exit: (Int) -> Unit = { kotlin.system.exitProcess(it) },
): Launch {
    var resolvedMode: Mode? = null
    val emit: (Mode) -> Unit = { resolvedMode = it }
    val root = BattletechTui(terminal, exit, emit)
        .subcommands(HotSeatCommand(emit), HostCommand(emit), JoinCommand(emit), ServerCommand(emit))
    root.main(args.toList())
    return Launch(
        mapPaths = root.mapPaths,
        mechPaths = root.mechPaths,
        unitPaths = root.unitPaths,
        themeName = root.themeName,
        mode = checkNotNull(resolvedMode) { "no Mode was resolved from ${args.toList()}" },
    )
}
