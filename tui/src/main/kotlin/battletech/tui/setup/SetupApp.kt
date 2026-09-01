package battletech.tui.setup

import battletech.tactical.model.content.AssetRegistry
import battletech.tactical.model.content.MatchPlan
import battletech.tui.input.Keybindings
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

internal sealed interface SetupOutcome {
    data class Commit(val plan: MatchPlan, val registry: AssetRegistry) : SetupOutcome

    /** The mirror's match started; the joiner has nothing to commit. */
    data object MatchStarted : SetupOutcome
    data object Quit : SetupOutcome
}

/**
 * Runs the interactive setup screen to completion. Mirrors `battletech.tui.TuiApp` but *accepts*
 * its terminal/renderer rather than constructing them (D17), so `Main.kt` can enter raw mode once
 * and hand the same [Terminal]/[ScreenRenderer] to whichever app runs next.
 */
internal class SetupApp(
    private val terminal: Terminal,
    private val renderer: ScreenRenderer,
    private val keys: Keybindings,
    private val lobby: SetupLobby,
    private val initial: SetupState,
) {
    /** Runs until the user commits or quits. */
    fun run(): SetupOutcome = runBlocking {
        val internalEvents = Channel<SetupUiEvent>(Channel.UNLIMITED)
        lobby.subscribe { internalEvents.trySend(SetupUiEvent.Lobby(it)) }

        setupLoop(
            events = merge(
                terminal.inputEvents(MouseTracking.Normal, isQuit = keys::isQuit).map { it.toSetupUiEvent() },
                terminal.resizeEvents().map { it.toSetupUiEvent() },
                internalEvents.receiveAsFlow(),
            ),
            internalEvents = internalEvents,
            terminal = terminal,
            renderer = renderer,
            initialState = initial,
            keys = keys,
            lobby = lobby,
        )
    }
}

private fun TerminalEvent.toSetupUiEvent(): SetupUiEvent = when (this) {
    is TerminalEvent.Input -> SetupUiEvent.Input(event)
    is TerminalEvent.Resized -> SetupUiEvent.Resized(Size(size.width, size.height))
    TerminalEvent.Quit -> SetupUiEvent.Quit
}
