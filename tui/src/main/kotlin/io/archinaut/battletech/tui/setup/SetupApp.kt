package io.archinaut.battletech.tui.setup

import io.archinaut.battletech.tactical.model.content.AssetRegistry
import io.archinaut.battletech.tactical.model.content.MatchPlan
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.battletech.tui.input.uiEvents
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import io.archinaut.tenter.terminal.Terminal
import io.archinaut.tenter.terminal.TerminalEvent

internal sealed interface SetupOutcome {
    data class Commit(val plan: MatchPlan, val registry: AssetRegistry) : SetupOutcome

    /** The mirror's match started; the joiner has nothing to commit. */
    data object MatchStarted : SetupOutcome
    data object Quit : SetupOutcome
}

/**
 * Runs the interactive setup screen to completion. Mirrors `io.archinaut.battletech.tui.TuiApp` but *accepts*
 * its terminal rather than constructing it (D17), so `Main.kt` can keep one
 * [io.archinaut.tenter.terminal.withScreen] scope across whichever app runs next. That scope owns the
 * alternate screen and cursor; this app's collected input flow owns its own raw-mode lifetime.
 */
internal class SetupApp(
    private val screen: Terminal,
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
                screen.uiEvents(keys, quit = SetupUiEvent.Quit) { it.toSetupUiEvent() },
                internalEvents.receiveAsFlow(),
            ),
            internalEvents = internalEvents,
            screen = screen,
            initialState = initial,
            keys = keys,
            lobby = lobby,
        )
    }
}

private fun TerminalEvent.toSetupUiEvent(): SetupUiEvent = when (this) {
    is TerminalEvent.Input -> SetupUiEvent.Input(event)
    is TerminalEvent.Resized -> SetupUiEvent.Resized(size)
}
