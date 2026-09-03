package battletech.tui.setup

import battletech.tactical.model.content.summarize
import battletech.tui.hex.HexGeometry
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
import tenter.input.PanAction
import tenter.input.ScrollAction
import tenter.screen.ScreenRenderer
import tenter.view.FlashMessage

/** Panel focus order the `Enter`/`Tab` cycle walks — HELP is never in it (see [nextPanel]'s KDoc). */
private val PANEL_CYCLE_ORDER = listOf(SetupPanelId.MODE, SetupPanelId.MAP, SetupPanelId.PLAYER_1, SetupPanelId.PLAYER_2)

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
    var done = false

    val workspace = SetupWorkspace(keys)

    fun render(forgetReveal: Boolean = false) {
        val buffer = workspace.render(state, size.width, size.height, activeFlash, forgetReveal)
        renderer.render(buffer)
    }

    /**
     * Ends the loop with [result]. `Flow.collect` has no "break", and throwing out of it is the
     * one thing this loop must never do (see the guard below) — so finishing is spelled the same
     * way quitting already is: post the sentinel [SetupUiEvent.Quit] that `takeWhile` stops on,
     * and ignore whatever else is already queued ahead of it via [done].
     */
    fun finish(result: SetupOutcome) {
        outcome = result
        done = true
        internalEvents.trySend(SetupUiEvent.Quit)
    }

    /** Shows [message] until its own expiry event comes back — one live flash at a time. */
    fun flash(message: String) {
        val shown = FlashMessage(message)
        activeFlash = shown
        flashGeneration++
        val gen = flashGeneration
        flashJob?.cancel()
        flashJob = launch {
            delay(shown.duration)
            internalEvents.send(SetupUiEvent.FlashExpired(gen))
        }
    }

    render(forgetReveal = true)

    events.takeWhile { it != SetupUiEvent.Quit }.collect { ui ->
        if (done) return@collect // an outcome is settled; drain until the Quit sentinel lands
        // See runLoop's identical guard: a throw out of collect would cancel this
        // coroutineScope (and the terminal input producer with it), stranding the terminal in
        // raw mode — so nothing, including a finished outcome, leaves this block by throwing.
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
                                    if (panel in SetupPanelVisibility.visiblePanels(state)) workspace.focusOrCycle(panel)
                                }
                                ChromeAction.ToggleHelp ->
                                    if (state.helpOpen && workspace.focused == SetupPanelId.HELP) {
                                        state = state.copy(helpOpen = false)
                                        workspace.focus(focusBeforeHelp ?: SetupPanelId.MODE)
                                    } else {
                                        focusBeforeHelp = workspace.focused
                                        state = state.copy(helpOpen = true)
                                        workspace.focus(SetupPanelId.HELP)
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

                        is PanAction -> {
                            if (action is PanAction.Pan) {
                                val (dx, dy) = when (action.direction) {
                                    PanAction.Direction.LEFT -> -HexGeometry.COL_STRIDE to 0
                                    PanAction.Direction.RIGHT -> HexGeometry.COL_STRIDE to 0
                                    PanAction.Direction.UP -> 0 to -HexGeometry.ROW_STRIDE
                                    PanAction.Direction.DOWN -> 0 to HexGeometry.ROW_STRIDE
                                }
                                workspace.scrollFocused(dx, dy)
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
                            val previousPlan = state.plan
                            state = newState
                            // Only a CHANGED plan is worth a wire message: cursor movement
                            // is deliberately not mirrored (D14), and it is by far the most
                            // frequent action here.
                            if (state.mode == SetupMode.HOST && state.plan != previousPlan) lobby.publish(state.plan)

                            val committed = transition.committed
                            if (committed != null) {
                                finish(SetupOutcome.Commit(committed, state.registry))
                                return@collect
                            }

                            if (!wasRostersVisible && state.rostersVisible) workspace.focus(SetupPanelId.MAP)

                            transition.flash?.let { flash(it.text) }

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
                        val revealing = !state.rostersVisible
                        state = state.copy(
                            registry = event.registry,
                            catalog = event.registry.summarize(),
                            opponentConnected = true,
                            opponentEverConnected = true,
                        )
                        // Only steal focus on the reveal itself — a reconnect mid-setup must
                        // not yank the user out of the panel they were editing.
                        if (revealing) workspace.focus(SetupPanelId.MAP)
                        render(forgetReveal = true)
                    }
                    LobbyEvent.OpponentLeft -> {
                        // Panels and selections stay (opponentEverConnected is latched); only
                        // the commit gate closes, and the flash says why.
                        state = state.copy(opponentConnected = false)
                        flash("player 2 disconnected — waiting for them to rejoin")
                        render()
                    }
                    is LobbyEvent.SelectionsChanged -> {
                        state = state.copy(plan = event.plan)
                        render()
                    }
                    LobbyEvent.MatchStarted -> {
                        finish(SetupOutcome.MatchStarted)
                        return@collect
                    }
                }

                SetupUiEvent.Quit -> Unit // unreachable — filtered by takeWhile
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            System.err.println("Unhandled throwable while processing $ui:")
            e.printStackTrace()
        }
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
