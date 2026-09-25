package io.archinaut.battletech.tui

import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.session.GameSession
import io.archinaut.battletech.tui.game.AppState
import io.archinaut.battletech.tui.game.mapToTuiPhase
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.battletech.tui.loop.UiEvent
import io.archinaut.battletech.tui.loop.runLoop
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import io.archinaut.tenter.screen.ScreenRenderer
import io.archinaut.tenter.terminal.TerminalEvent
import io.archinaut.tenter.terminal.inputEvents
import io.archinaut.tenter.terminal.resizeEvents

/**
 * [seats] is the set of seats this process drives, each mapped to the [GameSession] that seat
 * acts and reads through — see [AppState]'s KDoc for the full rationale. The caller composes this
 * map and hands it a session that has already been started (kickstarted, if applicable):
 * hot-seat's shared [io.archinaut.battletech.tactical.session.BattleSession] has already had
 * [io.archinaut.battletech.tactical.session.BattleSession.advance] called on it, a `host` seat's server
 * fires its own kickstart once the roster completes, and a `join`ed
 * [io.archinaut.battletech.network.client.ClientGameSession] never kickstarts at all. This class never builds
 * a session or calls `advance()` itself.
 *
 * [terminal]/[renderer] are accepted, not constructed (D17): `Main.kt` builds one [Terminal] +
 * [ScreenRenderer] inside one [io.archinaut.tenter.terminal.withScreen] scope for the whole process and hands
 * the same pair to both the setup screen ([io.archinaut.battletech.tui.setup.SetupApp]) and this class. The
 * scope owns alternate-screen/cursor cleanup; this app's cold input flow acquires and releases
 * raw mode while it is collected, with no flicker at the hand-off between the two screens.
 */
public class TuiApp(
    private val seats: Map<PlayerId, GameSession>,
    private val terminal: Terminal,
    private val renderer: ScreenRenderer,
) {

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
     * Dispatchers.IO — that is handled internally by [io.archinaut.tenter.terminal.inputEvents].
     *
     * ### Quit is not flow cancellation
     * Quit is detected inside [io.archinaut.tenter.terminal.inputEvents] using the keymap's predicate, which emits a
     * [TerminalEvent.Quit], mapped below to [UiEvent.Quit], and then naturally completes its
     * flow. We never cancel the flow externally as a quit mechanism — doing so would leave the
     * terminal in raw mode.
     */
    public fun run() {
        val keys = Keybindings.DEFAULT

        val appState = AppState(
            seats = seats,
            phase = mapToTuiPhase(seats.values.first().currentPhase),
            cursor = HexCoordinates(0, 0),
        )

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
    }
}

/** tenter's generic [TerminalEvent] mapped onto this app's own [UiEvent] hierarchy — see [UiEvent]'s KDoc for why it has a [UiEvent.Session] arm tenter cannot know about. */
private fun TerminalEvent.toUiEvent(): UiEvent = when (this) {
    is TerminalEvent.Input -> UiEvent.Input(event)
    is TerminalEvent.Resized -> UiEvent.Resized(Size(size.width, size.height))
    TerminalEvent.Quit -> UiEvent.Quit
}
