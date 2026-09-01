package battletech.tui

import battletech.network.client.JoinRejectedException
import battletech.network.client.ClientGameSession
import battletech.network.server.GameServer
import battletech.network.server.SocketAcceptor
import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId
import battletech.tactical.model.content.AssetBundle
import battletech.tactical.model.content.AssetRegistry
import battletech.tactical.model.content.ContentCatalog
import battletech.tactical.model.content.summarize
import battletech.tactical.model.map.MapLoadException
import battletech.tactical.model.mech.MechLoadException
import battletech.tactical.model.unit.UnitLoadException
import battletech.tactical.query.projectFor
import battletech.tactical.session.GameEvent
import battletech.tactical.unit.AutoDeploy
import battletech.tui.input.Keybindings
import battletech.tui.screen.Theme
import battletech.tui.screen.ThemeLoadException
import battletech.tui.screen.defaultThemeName
import battletech.tui.screen.resolveTheme
import battletech.tui.setup.NoLobby
import battletech.tui.setup.SetupApp
import battletech.tui.setup.SetupOutcome
import battletech.tui.setup.SetupState
import battletech.tui.view.GameLogFormatter
import com.github.ajalt.mordant.terminal.Terminal
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlin.io.path.Path
import tenter.screen.ScreenRenderer

/** Builds the [ContentCatalog] for one launch: every built-in plus [launch]'s `--add-*` registrations. */
private fun resolveContentOrExit(launch: Launch): ContentCatalog = try {
    ContentCatalog.load(launch.mapPaths.map(::Path), launch.mechPaths.map(::Path), launch.unitPaths.map(::Path))
} catch (e: MapLoadException) {
    System.err.println(e.message)
    kotlin.system.exitProcess(2)
} catch (e: MechLoadException) {
    System.err.println(e.message)
    kotlin.system.exitProcess(2)
} catch (e: UnitLoadException) {
    System.err.println(e.message)
    kotlin.system.exitProcess(2)
}

/** Assembles [setup]'s board/roster selection out of [content] into a validated starting [GameState]. */
private fun resolveGameOrExit(content: ContentCatalog, setup: Setup): GameState = try {
    content.resolveGame(setup.mapName, setup.unitsName)
} catch (e: MapLoadException) {
    System.err.println(e.message)
    kotlin.system.exitProcess(2)
} catch (e: UnitLoadException) {
    System.err.println(e.message)
    kotlin.system.exitProcess(2)
}

private fun resolveThemeOrExit(themeName: String?): Theme? = themeName?.let {
    try {
        resolveTheme(it)
    } catch (e: ThemeLoadException) {
        System.err.println(e.message)
        kotlin.system.exitProcess(2)
    }
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
 * Enters raw mode once and leaves it once (D17): constructs the one [Terminal] + [ScreenRenderer]
 * this process uses for its whole run — whether that means only [TuiApp], or [SetupApp] followed
 * by [TuiApp] on the interactive path — and hands both to [block]. [themeName] null auto-selects
 * from the terminal's detected color support, exactly as [TuiApp] used to do internally.
 */
private fun withScreen(themeName: String?, block: (Terminal, ScreenRenderer) -> Unit) {
    val terminal = Terminal()
    val theme = resolveThemeOrExit(themeName) ?: resolveTheme(defaultThemeName(terminal.terminalInfo.ansiLevel))
    val renderer = ScreenRenderer(terminal, theme)
    renderer.clear()
    try {
        block(terminal, renderer)
    } finally {
        renderer.cleanup()
    }
}

/**
 * Entry point for the TUI application.
 * Processes command-line arguments and launches the [TuiApp].
 */
public fun main(args: Array<String>) {
    val launch = parseArgs(args)

    when (val mode = launch.mode) {
        is Mode.HotSeat -> {
            val content = resolveContentOrExit(launch)
            val server = GameServer.host(resolveGameOrExit(content, mode.setup), content.contribution())
            // Build the map from each returned session's OWN playerId — connectLocal() assigns
            // seats via (allSeats - clients.keys).min(), not call order, so the Nth call is not
            // guaranteed to be the Nth PlayerId. See GameServer.connectLocal's KDoc.
            // The SAME bundle goes to every seat (D-Q18: re-registering an identical asset is a
            // silent no-op) — no branch on which seat this is.
            val seats = List(PlayerId.entries.size) { server.connectLocal(content.contribution()) }.associateBy { it.playerId }
            check(seats.keys == PlayerId.entries.toSet()) {
                "hot-seat roster incomplete: expected ${PlayerId.entries.toSet()}, got ${seats.keys}"
            }
            // TuiApp reads currentPhase to build its initial AppState, so composition must
            // absorb the kickstart race here — see awaitKickstart's KDoc.
            awaitKickstart(server, seats)
            server.use { withScreen(launch.themeName) { terminal, renderer -> TuiApp(seats, terminal, renderer).run() } }
        }

        is Mode.Host -> {
            val content = resolveContentOrExit(launch)
            val server = GameServer.host(resolveGameOrExit(content, mode.setup), content.contribution())
            // connectLocal() BEFORE the acceptor starts — see GameServer.connectLocal's KDoc
            // for why that order is what makes the local seat deterministically PLAYER_1.
            val localSession = server.connectLocal(content.contribution())
            val acceptor = SocketAcceptor(server, mode.port)
            acceptor.start()
            println("Session ID: ${server.sessionId} — listening on port ${acceptor.boundPort}")
            acceptor.use {
                server.use {
                    withScreen(launch.themeName) { terminal, renderer ->
                        TuiApp(seats = mapOf(localSession.playerId to localSession), terminal, renderer).run()
                    }
                }
            }
        }

        is Mode.Join -> {
            // --add-map/--add-mech contribute this seat's registered content to the host's
            // shared asset registry, even though join never SELECTS a map or unit collection of
            // its own — see ContentCatalog.contribution's KDoc.
            val content = resolveContentOrExit(launch)
            val remote = try {
                ClientGameSession.connect(mode.host, mode.port, mode.sessionId, content.contribution())
            } catch (e: JoinRejectedException) {
                System.err.println("Join rejected: ${e.reason}")
                kotlin.system.exitProcess(1)
            } catch (e: IOException) {
                System.err.println("Could not connect to ${mode.host}:${mode.port}: ${e.message}")
                kotlin.system.exitProcess(1)
            }
            remote.use { remote ->
                withScreen(launch.themeName) { terminal, renderer ->
                    TuiApp(seats = mapOf(remote.playerId to remote), terminal, renderer).run()
                }
            }
        }

        // --theme is accepted at the root for every mode but is meaningless for a headless
        // server, so launch.themeName is simply never read on this path — no branch needed.
        is Mode.Server -> {
            val content = resolveContentOrExit(launch)
            runHeadlessServer(mode.port, resolveGameOrExit(content, mode.setup), content.contribution())
        }

        // Bare invocation (D1). Landing 1: hot-seat only — the lobby (host/join) wiring lands in
        // a later commit, replacing NoLobby with an adapter over battletech.network without any
        // other change to this branch (D12). Nothing is printed on this path (D3): a stray
        // println would corrupt the setup screen's first frame.
        Mode.Interactive -> {
            val content = resolveContentOrExit(launch)
            val registry = AssetRegistry.EMPTY.merge(content.contribution()).registry
            withScreen(launch.themeName) { terminal, renderer ->
                val initial = SetupState(catalog = registry.summarize(), registry = registry)
                when (val outcome = SetupApp(terminal, renderer, Keybindings.DEFAULT, NoLobby, initial).run()) {
                    SetupOutcome.Quit -> Unit
                    SetupOutcome.MatchStarted -> Unit // unreachable for NoLobby
                    is SetupOutcome.Commit -> {
                        val state = AutoDeploy.deploy(outcome.plan, outcome.registry)
                        val server = GameServer.host(state, content.contribution())
                        val seats =
                            List(PlayerId.entries.size) { server.connectLocal(content.contribution()) }.associateBy { it.playerId }
                        check(seats.keys == PlayerId.entries.toSet()) {
                            "interactive roster incomplete: expected ${PlayerId.entries.toSet()}, got ${seats.keys}"
                        }
                        awaitKickstart(server, seats)
                        server.use { TuiApp(seats, terminal, renderer).run() }
                    }
                }
            }
        }
    }
}

/**
 * Headless dedicated server: no [TuiApp], no Mordant terminal. Both players connect
 * remotely via the `join` subcommand. Runs until Ctrl-C (or another SIGTERM), printing every game
 * event to stdout as it happens; the process stays up after [battletech.tactical.session.MatchEnded].
 * [content] is this launch's own registered catalog — contributed to the shared asset registry
 * the same way any other seat's is (D-Q24).
 */
private fun runHeadlessServer(port: Int, initialGameState: GameState, content: AssetBundle = AssetBundle.EMPTY) {
    val server = GameServer.host(initialGameState, content)

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
        lines.forEach { line -> out.append("${line.icon} ${line.text}\n") }
    }
}
