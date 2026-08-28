package battletech.tui

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.session.GameSession
import battletech.tui.game.AppState
import battletech.tui.game.mapToTuiPhase
import battletech.tui.input.Keybindings
import battletech.tui.loop.UiEvent
import battletech.tui.loop.runLoop
import battletech.tui.screen.Theme
import battletech.tui.screen.defaultThemeName
import battletech.tui.screen.resolveTheme
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import tenter.screen.ScreenRenderer
import tenter.terminal.TerminalEvent
import tenter.terminal.inputEvents
import tenter.terminal.resizeEvents

/**
 * [seats] is the set of seats this process drives, each mapped to the [GameSession] that seat
 * acts and reads through — see [AppState]'s KDoc for the full rationale. The caller composes this
 * map and hands it a session that has already been started (kickstarted, if applicable):
 * hot-seat's shared [battletech.tactical.session.BattleSession] has already had
 * [battletech.tactical.session.BattleSession.advance] called on it, a `host` seat's server
 * fires its own kickstart once the roster completes, and a `join`ed
 * [battletech.network.client.ClientGameSession] never kickstarts at all. This class never builds
 * a session or calls `advance()` itself.
 *
 * [theme] selects the color theme; `null` auto-selects from the terminal's detected color support
 * (see [ScreenRenderer]'s KDoc and [defaultThemeName]) by loading the matching packaged theme —
 * that load can therefore throw [battletech.tui.screen.ThemeLoadException] if the jar's own
 * `theme/` resources are missing or malformed, before raw mode is ever entered.
 */
public class TuiApp(private val seats: Map<PlayerId, GameSession>, private val theme: Theme? = null) {

    /**
     * Entry point. Wires a subscription for every seat's session into [internalEvents], merges
     * all event sources, and drives [runLoop].
     *
     * ### One subscription per seat, not deduplicated by session identity
     * Hot-seat's two seats share the SAME underlying session object, so subscribing per seat
     * (rather than per distinct session) delivers each event twice there — renders are
     * idempotent, so that's wasteful, not wrong, and keeps this method free of any "is this the
     * same session as another seat?" special-casing. Host/join seat maps have exactly one entry,
     * so they see no duplication at all.
     *
     * ### Single-thread confinement
     * All session mutations, AppState updates, and rendering run on the single
     * [runBlocking] (main) thread. Only the terminal input producer runs on
     * Dispatchers.IO — that is handled internally by [tenter.terminal.inputEvents].
     *
     * ### Quit is not flow cancellation
     * Quit is detected inside [tenter.terminal.inputEvents] (ctrl+c) which emits a
     * [TerminalEvent.Quit], mapped below to [UiEvent.Quit], and then naturally completes its
     * flow. We never cancel the flow externally as a quit mechanism — doing so would leave the
     * terminal in raw mode.
     */
    public fun run() {
        val terminal = Terminal()
        val resolvedTheme = theme ?: resolveTheme(defaultThemeName(terminal.terminalInfo.ansiLevel))
        val renderer = ScreenRenderer(terminal, resolvedTheme)
        val keys = Keybindings.DEFAULT

        val appState = AppState(
            seats = seats,
            phase = mapToTuiPhase(seats.values.first().currentPhase),
            cursor = HexCoordinates(0, 0),
        )

        renderer.clear()
        try {
            runBlocking {
                val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)
                val subscriptions = seats.values.map { session ->
                    session.subscribe { internalEvents.trySend(UiEvent.Session(it)) }
                }
                try {
                    runLoop(
                        events = merge(
                            terminal.inputEvents(MouseTracking.Normal, isQuit = keys::isQuit).map { it.toUiEvent() },
                            terminal.resizeEvents().map { it.toUiEvent() },
                            internalEvents.receiveAsFlow(),
                        ),
                        internalEvents = internalEvents,
                        terminal = terminal,
                        renderer = renderer,
                        initialState = appState,
                        keys = keys,
                    )
                } finally {
                    subscriptions.forEach { it.unsubscribe() }
                }
            }
        } finally {
            renderer.cleanup()
        }
    }
}

/** tenter's generic [TerminalEvent] mapped onto this app's own [UiEvent] hierarchy — see [UiEvent]'s KDoc for why it has a [UiEvent.Session] arm tenter cannot know about. */
private fun TerminalEvent.toUiEvent(): UiEvent = when (this) {
    is TerminalEvent.Input -> UiEvent.Input(event)
    is TerminalEvent.Resized -> UiEvent.Resized(Size(size.width, size.height))
    TerminalEvent.Quit -> UiEvent.Quit
}
