package battletech.tui.loop

import battletech.tactical.attack.AttackResult
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.MatchEnded
import battletech.tui.animation.WeaponAnimations
import battletech.tui.animation.PanelPlacement
import battletech.tui.game.AppState
import battletech.tui.game.GamePanelId
import battletech.tui.game.PanelVisibility
import battletech.tui.game.mapToTuiPhase
import battletech.tui.game.phase.BOARD_ORIGIN_X
import battletech.tui.game.phase.BOARD_ORIGIN_Y
import battletech.tui.hex.HexGeometry
import battletech.tui.input.BoardClick
import battletech.tui.input.BoardMouse
import battletech.tui.input.ChromeAction
import battletech.tui.input.ContextId
import battletech.tui.input.Keybindings
import battletech.tui.view.Workspace
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
import tenter.animation.AnimationPlayback
import tenter.input.InputAction
import tenter.input.MouseInput
import tenter.input.PanAction
import tenter.input.ScrollAction
import tenter.view.FlashMessage
import tenter.screen.ScreenRenderer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Headless-testable event loop. Collects [events] until [UiEvent.Quit] is seen.
 *
 * Flash jobs are launched in the enclosing [coroutineScope]; they post [UiEvent.FlashExpired]
 * back through [internalEvents]. The active flash job is cancelled after the collect loop
 * returns so no coroutine outlives the loop. The weapon-fire animation overlay uses one
 * elapsed-time wake-up at a time; stale wake-ups are rejected by a token.
 *
 * [buildVolley] turns a resolved volley's results into the overlay that plays for it — one panel
 * per distinct weapon category fired, already placed on a screen of the given size. Defaults to
 * [WeaponAnimations.volleyFor]; a test overrides it to pin both which animations play and where.
 */
internal suspend fun runLoop(
    events: Flow<UiEvent>,
    internalEvents: SendChannel<UiEvent>,
    terminal: Terminal,
    renderer: ScreenRenderer,
    initialState: AppState,
    keys: Keybindings,
    buildVolley: (List<AttackResult>, Int, Int) -> AnimationPlayback<PanelPlacement>? =
        { results, width, height -> WeaponAnimations.volleyFor(results, width, height) },
    nowNanos: () -> Long = System::nanoTime,
): Unit = coroutineScope {
    var appState = initialState
    var activeFlash: FlashMessage? = null
    var flashGeneration = 0L
    var flashJob: Job? = null
    data class ActiveAnimation(
        val playback: AnimationPlayback<PanelPlacement>,
        val startedAtNanos: Long,
    )
    var activeAnimation: ActiveAnimation? = null
    var animationTimer: Job? = null
    var animationToken = 0L
    var size = currentSize(terminal)

    // One Workspace for this whole run: every panel remembers its own state (minimized/normal/
    // maximized), scroll offset, and auto-follow reveal across frames (see Panel's KDoc) —
    // nothing but the board's scroll offset round-trips back through appState (see
    // AppState.boardScroll's KDoc).
    val workspace = Workspace(keys)

    fun invalidateAnimationTimer() {
        animationToken++
        animationTimer?.cancel()
        animationTimer = null
    }

    fun stopVolley() {
        activeAnimation = null
        invalidateAnimationTimer()
    }

    fun elapsedSince(startedAtNanos: Long): Duration =
        (nowNanos() - startedAtNanos).coerceAtLeast(0L).nanoseconds

    fun scheduleAnimationWake(token: Long, delayDuration: Duration) {
        animationTimer = launch {
            delay(delayDuration)
            internalEvents.send(UiEvent.AnimationTick(token))
        }
    }

    /**
     * Every render goes through here so the board's settled scroll offset is folded back into
     * [appState] before the next event is handled — otherwise a click or wheel tick right after a
     * pan/follow would hit-test or scroll from a stale offset.
     *
     * [forgetReveal] tells every panel (and the board) to treat their content's reveal as freshly
     * arrived rather than compared against what was last settled: used on resize, where the
     * viewport changes but content-space reveal does not, and a shrink could otherwise strand the
     * cursor off-screen.
     */
    fun render(forgetReveal: Boolean = false) {
        val active = activeAnimation
        val sampleElapsed = if (active == null) {
            null
        } else {
            elapsedSince(active.startedAtNanos)
        }
        val sample = if (active == null) {
            null
        } else {
            try {
                active.playback.sample(checkNotNull(sampleElapsed))
            } catch (t: Throwable) {
                stopVolley()
                throw t
            }
        }
        if (active != null && sample?.nextChangeIn == null) {
            stopVolley()
        }
        try {
            val buffer = workspace.render(
                appState,
                size.width,
                size.height,
                activeFlash,
                forgetReveal,
                sample?.frames.orEmpty(),
            )
            renderer.render(buffer)
            appState = appState.copy(boardScroll = workspace.boardOffset)

            if (active != null && activeAnimation === active) {
                val nextChange = sample?.nextChangeIn
                if (nextChange != null) {
                    val elapsedAfterRender = elapsedSince(active.startedAtNanos)
                    val deadline = checkNotNull(sampleElapsed) + nextChange
                    val remaining = deadline - elapsedAfterRender
                    invalidateAnimationTimer()
                    val token = animationToken
                    scheduleAnimationWake(token, if (remaining > Duration.ZERO) remaining else 1.milliseconds)
                }
            }
        } catch (t: Throwable) {
            if (activeAnimation != null) stopVolley()
            throw t
        }
    }

    /** Starts one elapsed-time playback and lets the triggering render show frame zero. */
    fun startVolley(results: List<AttackResult>) {
        val started = buildVolley(results, size.width, size.height) ?: return
        invalidateAnimationTimer()
        activeAnimation = ActiveAnimation(started, nowNanos())
    }

    // Render the initial frame before collecting any events. forgetReveal = true so every panel
    // (and the board) follows its reveal target into view for the first time, exactly as a resize does.
    render(forgetReveal = true)

    events.takeWhile { it != UiEvent.Quit }.collect { ui ->
        // A single bad event must not propagate out of collect: that would cancel this
        // coroutineScope and, with it, the terminal input producer running on Dispatchers.IO —
        // exactly the external-cancellation hazard documented on Terminal.terminalInputEvents.
        // This applies equally to an Exception or an Error (e.g. NoClassDefFoundError/LinkageError
        // from a jar rewritten under a live JVM), so we catch Throwable rather than Exception.
        try {
            when (ui) {
                is UiEvent.Input -> {
                    val event = ui.event

                    // While the weapon-fire overlay is playing, it owns input entirely: only Esc is
                    // live (cancels every panel at once), every other key/click is swallowed rather
                    // than reaching the phase underneath opaque panels the user can't see through.
                    // "Playing" starts the instant the volley does, including while later panels
                    // are still waiting out their stagger.
                    if (activeAnimation != null) {
                        if (event is KeyboardEvent && event == KeyboardEvent("Escape")) {
                            stopVolley()
                            render()
                        }
                        return@collect
                    }

                    // Handle scroll events before any other input dispatch.
                    // The panel is looked up first so overPanel can be passed to scrollDelta,
                    // which applies the Mordant posix wheel-parsing workaround (left/right
                    // press over a panel treated as wheel-up/down; see MouseInput.scrollDelta).
                    if (event is MouseEvent) {
                        val panelId = workspace.panelAt(event.x, event.y)
                        val delta = MouseInput.scrollDelta(event, overPanel = panelId != null)
                        if (delta != null) {
                            panelId?.let { workspace.scrollPanel(it, delta) }
                            render()
                            return@collect  // scroll events never reach phases
                        }
                    }

                    val action = resolveInput(event, keys, workspace.focused, appState)

                    when (action) {
                        null -> {
                            render()
                            return@collect
                        }

                        is ChromeAction -> {
                            when (action) {
                                is ChromeAction.FocusPanel -> {
                                    // GAME_CHROME is the only layer binding FocusPanel while a game is
                                    // running, and it only ever names a GamePanelId.
                                    val panel = action.panel as GamePanelId
                                    if (panel == GamePanelId.BOARD || panel in PanelVisibility.visiblePanels(appState)) {
                                        workspace.focusOrCycle(panel)
                                    }
                                }
                                // ? is a different action from every other panel's Alt+<key>: it also
                                // toggles whether HELP EXISTS this frame (an AppState fact PanelVisibility
                                // reads), not just a focus request — see AppState.helpOpen's KDoc.
                                ChromeAction.ToggleHelp ->
                                    if (appState.helpOpen && workspace.focused == GamePanelId.HELP) {
                                        appState = appState.copy(helpOpen = false)
                                        workspace.focus(GamePanelId.BOARD)
                                    } else {
                                        appState = appState.copy(helpOpen = true)
                                        workspace.focus(GamePanelId.HELP)
                                    }
                                is ChromeAction.CycleState -> workspace.cycleFocusedState(action.delta)
                                // Absorbed by terminalEvents' takeWhile — unreachable here by construction.
                                ChromeAction.Quit -> Unit
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
                            when (action) {
                                is PanAction.Pan -> {
                                    val (dx, dy) = when (action.direction) {
                                        PanAction.Direction.LEFT -> -HexGeometry.COL_STRIDE to 0
                                        PanAction.Direction.RIGHT -> HexGeometry.COL_STRIDE to 0
                                        PanAction.Direction.UP -> 0 to -HexGeometry.ROW_STRIDE
                                        PanAction.Direction.DOWN -> 0 to HexGeometry.ROW_STRIDE
                                    }
                                    workspace.panBoard(dx, dy)
                                }
                                PanAction.Recenter -> workspace.recenterBoard()
                            }
                            render()
                            return@collect
                        }

                        else -> Unit // game action — falls through to the phase below
                    }

                    val transition = appState.phase.handle(action, appState) ?: run {
                        render()
                        return@collect
                    }
                    appState = transition.app

                    val flash = transition.flash
                    if (flash != null) {
                        activeFlash = flash
                        flashGeneration++
                        val gen = flashGeneration
                        flashJob?.cancel()
                        flashJob = launch {
                            delay(flash.duration)
                            internalEvents.send(UiEvent.FlashExpired(gen))
                        }
                    }

                    render()
                }

                is UiEvent.Resized -> {
                    size = ui.size
                    // Panels were placed against the OLD size, so any resize invalidates the whole
                    // layout — not just one that no longer fits. Cancelling is honest and cheap;
                    // re-solving placements mid-flight would make panels jump around instead.
                    if (activeAnimation != null) stopVolley()
                    // Content-space reveal doesn't change on resize, so without forgetting it a
                    // shrink could leave the cursor stranded outside the new viewport.
                    render(forgetReveal = true)
                }

                is UiEvent.FlashExpired -> {
                    if (ui.generation == flashGeneration) {
                        activeFlash = null
                        render()
                    }
                    // Stale expiry (earlier flash replaced by a newer one): ignore.
                }

                is UiEvent.AnimationTick -> {
                    if (activeAnimation != null && ui.token == animationToken) {
                        render()
                    }
                    // Stale tick or cancelled playback: ignore.
                }

                // Re-render only: the renderer re-reads state through the session.
                // Locally these events arrive synchronously during submitCommand so
                // the extra render is cheap and idempotent. For remote play the
                // opponent's commands land here asynchronously (via the session's
                // subscription) without a local Transition ever having run, so the
                // TUI phase can go stale — the resync below catches it up.
                is UiEvent.Session -> {
                    val resynced = mapToTuiPhase(appState.anySession.currentPhase)
                    val isResync = resynced.turnPhase != appState.phase.turnPhase
                    appState = appState.copy(
                        matchEnded = (ui.event as? MatchEnded) ?: appState.matchEnded,
                        phase = if (isResync) resynced else appState.phase,
                        lastAttackResults = when {
                            ui.event is AttacksResolved -> ui.event.results
                            isResync && resynced.turnPhase == TurnPhase.WEAPON_ATTACK -> null
                            else -> appState.lastAttackResults
                        },
                    )
                    // Celebrate a resolved volley with the weapon-fire overlay — guarded on
                    // `activeAnimation == null` so hot-seat's double delivery (both seats share one
                    // session, so every event arrives once per seat subscription — see
                    // TuiApp.run's KDoc) starts exactly one volley, not two. Volley construction
                    // owns the per-animation fit check, after it knows the concrete animations.
                    val resolved = ui.event
                    if (resolved is AttacksResolved && activeAnimation == null) {
                        startVolley(resolved.results)
                    }
                    render()
                }

                UiEvent.Quit -> {
                    // Unreachable inside collect (filtered by takeWhile).
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            System.err.println("Unhandled throwable while processing $ui:")
            e.printStackTrace()
        }
    }

    // Cancel any pending flash/animation job so the coroutineScope can complete cleanly.
    flashJob?.cancel()
    stopVolley()
}

/**
 * What [event] means in this frame, or null when nothing live is bound to it.
 *
 * Both halves of "what is live right now" live here, because they have to agree: a keyboard chord
 * reaches the phase only while [activeContexts] still includes the phase's own layer, and a board
 * click — which never goes through the keymap at all — needs the same match-ended gate applied by
 * hand, or clicking would still drive the game after the match was over.
 *
 * Split out of [runLoop]'s collect block so that agreement is directly testable. It cannot be
 * observed through rendered output: once `matchEnded` is set, `Workspace.render` swaps the status
 * bar for the match-over line and stops drawing flash text at all, so a blocked and an unblocked
 * input produce the same frame.
 */
internal fun resolveInput(
    event: InputEvent,
    keys: Keybindings,
    focused: GamePanelId,
    appState: AppState,
): InputAction? = when (event) {
    is KeyboardEvent -> keys.resolve(activeContexts(focused, appState), event)
    is MouseEvent ->
        if (appState.matchEnded != null) {
            null
        } else {
            BoardMouse.mapMouseToHex(
                event, boardX = BOARD_ORIGIN_X, boardY = BOARD_ORIGIN_Y,
                scrollX = appState.boardScroll.x, scrollY = appState.boardScroll.y,
            )?.let(::BoardClick)
        }
}

/**
 * Which key layers are live this frame, in resolution-precedence order. Game input
 * (the active phase's own context) is omitted once the match has ended — chrome (focus, resize,
 * pan, quit) stays live regardless.
 */
private fun activeContexts(focused: GamePanelId, appState: AppState): List<ContextId> = buildList {
    if (focused != GamePanelId.BOARD) add(ContextId.PANEL_SCROLL)
    add(ContextId.CHROME)
    add(ContextId.GAME_CHROME)
    if (appState.matchEnded == null) add(appState.phase.keyContext)
}

private fun currentSize(terminal: Terminal): Size {
    val size = terminal.updateSize()
    check(size.width > 0) { "Terminal width must be positive, got: $size" }
    check(size.height > 0) { "Terminal height must be positive, got: $size" }
    return Size(size.width, size.height)
}
