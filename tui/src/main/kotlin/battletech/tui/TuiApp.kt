package battletech.tui

import battletech.tactical.model.GameState
import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.session.BattleSession
import battletech.tactical.session.GameSession
import battletech.tactical.session.TurnState
import battletech.tui.game.AppState
import battletech.tui.game.mapToTuiPhase
import battletech.tui.loop.UiEvent
import battletech.tui.loop.resizeEvents
import battletech.tui.loop.runLoop
import battletech.tui.loop.terminalInputEvents
import battletech.tui.screen.ScreenRenderer
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking

public class TuiApp(
    private val providedSession: GameSession? = null,
    private val localPlayer: PlayerId? = null,
    private val initialGameState: GameState? = null,
) {

    /**
     * Entry point. Sets up the session, wires subscriptions for every player into
     * [internalEvents], merges all event sources, and drives [runLoop].
     *
     * When [providedSession] is null (hot-seat, the default) this builds a local
     * [BattleSession] and fires the [BattleSession.advance] kickstart itself, exactly
     * as before remote play existed. When non-null (host/join modes) the caller
     * owns session construction/kickstart — a host's [BattleSession] wrapped in
     * `GameServer` fires its own kickstart once a client joins; a joining client's
     * `RemoteGameSession` never kickstarts at all.
     *
     * ### Single-thread confinement
     * All session mutations, AppState updates, and rendering run on the single
     * [runBlocking] (main) thread. Only the terminal input producer runs on
     * Dispatchers.IO — that is handled internally by [terminalInputEvents].
     *
     * ### Quit is not flow cancellation
     * Quit is detected inside [terminalInputEvents] (ctrl+c) which emits [UiEvent.Quit]
     * and then naturally completes its flow. We never cancel the flow externally as a
     * quit mechanism — doing so would leave the terminal in raw mode.
     */
    public fun run() {
        val terminal = Terminal()
        val renderer = ScreenRenderer(terminal)

        val session = providedSession ?: BattleSession(
            initialGameState = initialGameState ?: GameStateFactory().sampleGameState(),
            initialTurnState = TurnState.NULL,
        ).also {
            // Kickstart cascades INITIATIVE → MOVEMENT; the session's gameLog
            // captures the cascade events and the LOG panel renders them.
            it.advance()
        }
        val appState = AppState(
            session = session,
            phase = mapToTuiPhase(session.currentPhase),
            cursor = HexCoordinates(0, 0),
            localPlayer = localPlayer,
        )

        renderer.clear()
        try {
            runBlocking {
                val internalEvents = Channel<UiEvent>(Channel.UNLIMITED)
                val subscription = session.subscribe { internalEvents.trySend(UiEvent.Session(it)) }
                try {
                    runLoop(
                        events = merge(
                            terminal.terminalInputEvents(MouseTracking.Normal),
                            terminal.resizeEvents(),
                            internalEvents.receiveAsFlow(),
                        ),
                        internalEvents = internalEvents,
                        terminal = terminal,
                        renderer = renderer,
                        initialState = appState,
                    )
                } finally {
                    subscription.unsubscribe()
                }
            }
        } finally {
            renderer.cleanup()
        }
    }
}
