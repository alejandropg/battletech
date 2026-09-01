package battletech.tui.setup

import battletech.tactical.model.content.summarize
import battletech.tui.input.ChromeAction
import battletech.tui.input.ContextId
import battletech.tui.input.Keybindings
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import tenter.input.InputAction
import tenter.input.MouseInput
import tenter.input.ScrollAction
import tenter.screen.ScreenRenderer
import tenter.view.FlashMessage

/** Panel focus order the `Enter` cycle walks — HELP is never in it (see [nextPanel]'s KDoc). */
private val PANEL_CYCLE_ORDER = listOf(SetupPanelId.MODE, SetupPanelId.MAP, SetupPanelId.PLAYER_1, SetupPanelId.PLAYER_2)

/** Thrown from inside [setupLoop]'s collect to stop it the instant a [SetupOutcome] is reached. */
private class SetupDone : RuntimeException()

/**
 * Headless-testable event loop for the interactive setup screen. Mirrors `battletech.tui.loop.
 * runLoop`'s shape deliberately, rather than generalising the two into one — see the plan this
 * package implements.
 */
internal suspend fun setupLoop(
    events: Flow<SetupUiEvent>,
    internalEvents: SendChannel<SetupUiEvent>,
    terminal: Terminal,
    renderer: ScreenRenderer,
    initialState: SetupState,
    keys: Keybindings,
    lobby: SetupLobby,
): SetupOutcome = coroutineScope {
    var state = initialState
    var activeFlash: FlashMessage? = null
    var flashGeneration = 0L
    var flashJob: Job? = null
    var size = currentSize(terminal)
    // Which panel to return focus to when HELP closes — one-shot, loop-local state, exactly as
    // the game's chrome never stores this either.
    var focusBeforeHelp: SetupPanelId? = null
    var outcome: SetupOutcome = SetupOutcome.Quit

    val workspace = SetupWorkspace(keys)

    fun render(forgetReveal: Boolean = false) {
        val buffer = workspace.render(state, size.width, size.height, activeFlash, forgetReveal)
        renderer.render(buffer)
    }

    render(forgetReveal = true)

    // Flow.collect has no "break". A Commit or MatchStarted result must stop this loop the
    // instant it is reached — the merged input flow otherwise idles forever waiting for a
    // keystroke that may never come. SetupDone signals that from inside collect and is caught
    // right outside it, so this function still returns normally instead of throwing.
    try {
        events.takeWhile { it != SetupUiEvent.Quit }.collect { ui ->
            // See runLoop's identical guard: a throw out of collect would cancel this
            // coroutineScope (and the terminal input producer with it), stranding the terminal in
            // raw mode. SetupDone is deliberately let through — that IS how this loop ends.
            try {
                when (ui) {
                    is SetupUiEvent.Input -> {
                        val event = ui.event

                        if (event is MouseEvent) {
                            val panelId = workspace.panelAt(event.x, event.y)
                            val delta = MouseInput.scrollDelta(event, overPanel = panelId != null)
                            if (delta != null) {
                                panelId?.let { workspace.scrollPanel(it, delta) }
                                render()
                                return@collect
                            }
                        }

                        val action = resolveSetupInput(event, keys)

                        when (action) {
                            null -> {
                                render()
                                return@collect
                            }

                            is ChromeAction -> {
                                when (action) {
                                    is ChromeAction.FocusPanel -> {
                                        // SETUP is the only layer binding FocusPanel here, and it
                                        // only ever names a SetupPanelId.
                                        val panel = action.panel as SetupPanelId
                                        if (panel in SetupPanelVisibility.visiblePanels(state)) workspace.focus(panel)
                                    }
                                    ChromeAction.ToggleHelp ->
                                        if (state.helpOpen && workspace.focused == SetupPanelId.HELP) {
                                            state = state.copy(helpOpen = false)
                                            workspace.focus(focusBeforeHelp ?: SetupPanelId.MODE)
                                        } else {
                                            focusBeforeHelp = workspace.focused
                                            state = state.copy(helpOpen = true)
                                            workspace.focus(SetupPanelId.HELP)
                                            workspace.cycleFocusedState(1) // NORMAL -> MAXIMIZED
                                        }
                                    is ChromeAction.CycleState -> workspace.cycleFocusedState(action.delta)
                                    ChromeAction.Quit -> Unit // absorbed upstream — see Keybindings.isQuit
                                }
                                render()
                                return@collect
                            }

                            is ScrollAction -> {
                                when (action) {
                                    is ScrollAction.Lines -> workspace.scrollFocused(0, action.delta)
                                    is ScrollAction.Pages -> workspace.pageFocused(action.delta)
                                }
                                render()
                                return@collect
                            }

                            SetupAction.NextPanel -> {
                                val order = PANEL_CYCLE_ORDER.filter { it in SetupPanelVisibility.visiblePanels(state) }
                                workspace.focus(nextPanel(workspace.focused, order))
                                render()
                                return@collect
                            }

                            is SetupAction -> {
                                val wasModeLocked = state.modeLocked
                                val wasRostersVisible = state.rostersVisible
                                val transition = handleSetup(action, workspace.focused, state) ?: run {
                                    render()
                                    return@collect
                                }

                                var newState = transition.state
                                if (!wasModeLocked && newState.modeLocked && newState.mode == SetupMode.HOST) {
                                    newState = newState.copy(endpoint = lobby.beginHosting())
                                }
                                state = newState
                                if (state.mode == SetupMode.HOST) lobby.publish(state.plan)

                                val committed = transition.committed
                                if (committed != null) {
                                    outcome = SetupOutcome.Commit(committed, state.registry)
                                    throw SetupDone()
                                }

                                if (!wasRostersVisible && state.rostersVisible) workspace.focus(SetupPanelId.MAP)

                                val flash = transition.flash
                                if (flash != null) {
                                    activeFlash = flash
                                    flashGeneration++
                                    val gen = flashGeneration
                                    flashJob?.cancel()
                                    flashJob = launch {
                                        delay(flash.duration)
                                        internalEvents.send(SetupUiEvent.FlashExpired(gen))
                                    }
                                }

                                render()
                            }

                            else -> Unit
                        }
                    }

                    is SetupUiEvent.Resized -> {
                        size = ui.size
                        render(forgetReveal = true)
                    }

                    is SetupUiEvent.FlashExpired -> {
                        if (ui.generation == flashGeneration) {
                            activeFlash = null
                            render()
                        }
                    }

                    is SetupUiEvent.Lobby -> when (val event = ui.event) {
                        is LobbyEvent.OpponentJoined -> {
                            state = state.copy(
                                registry = event.registry,
                                catalog = event.registry.summarize(),
                                opponentConnected = true,
                            )
                            workspace.focus(SetupPanelId.MAP)
                            render(forgetReveal = true)
                        }
                        is LobbyEvent.SelectionsChanged -> {
                            state = state.copy(plan = event.plan)
                            render()
                        }
                        LobbyEvent.MatchStarted -> {
                            outcome = SetupOutcome.MatchStarted
                            throw SetupDone()
                        }
                    }

                    SetupUiEvent.Quit -> Unit // unreachable — filtered by takeWhile
                }
            } catch (e: SetupDone) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                System.err.println("Unhandled throwable while processing $ui:")
                e.printStackTrace()
            }
        }
    } catch (e: SetupDone) {
        // Expected: outcome was already set to Commit or MatchStarted above.
    }

    flashJob?.cancel()
    outcome
}

private fun resolveSetupInput(event: InputEvent, keys: Keybindings): InputAction? = when (event) {
    is KeyboardEvent -> keys.resolve(listOf(ContextId.SETUP, ContextId.CHROME, ContextId.PANEL_SCROLL), event)
    is MouseEvent -> null
}

private fun currentSize(terminal: Terminal): Size {
    val size = terminal.updateSize()
    check(size.width > 0) { "Terminal width must be positive, got: $size" }
    check(size.height > 0) { "Terminal height must be positive, got: $size" }
    return Size(size.width, size.height)
}
