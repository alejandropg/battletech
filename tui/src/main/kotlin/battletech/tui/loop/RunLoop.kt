package battletech.tui.loop

import battletech.tactical.model.TurnPhase
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.MatchEnded
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.PanelId
import battletech.tui.game.PanelVisibility
import battletech.tui.game.mapToTuiPhase
import battletech.tui.input.InputMapper
import battletech.tui.input.PanAction
import battletech.tui.screen.ScreenRenderer
import battletech.tui.view.Frame
import battletech.tui.view.ScrollOffset
import battletech.tui.view.composeFrame
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

/**
 * Headless-testable event loop. Collects [events] until [UiEvent.Quit] is seen.
 *
 * Flash jobs are launched in the enclosing [coroutineScope]; they post [UiEvent.FlashExpired]
 * back through [internalEvents]. The active flash job is cancelled after the collect loop
 * returns so no coroutine outlives the loop.
 */
internal suspend fun runLoop(
    events: Flow<UiEvent>,
    internalEvents: SendChannel<UiEvent>,
    terminal: Terminal,
    renderer: ScreenRenderer,
    initialState: AppState,
): Unit = coroutineScope {
    var appState = initialState
    var activeFlash: FlashMessage? = null
    var flashGeneration = 0L
    var flashJob: Job? = null
    var size = currentSize(terminal)

    /**
     * Folds one composed frame's settled scroll positions back into [appState]: the board's
     * dense [AppState.boardScroll], and every panel actually rendered expanded this frame merged
     * into [AppState.panelScrollOffsets] (panels invisible or collapsed this frame keep whatever
     * they last had, so re-expanding one restores its position). Both must happen before the next
     * event is handled — otherwise a click or wheel tick right after a pan/follow would hit-test
     * or scroll from a stale offset.
     */
    fun syncScroll(composed: Frame) {
        appState = appState.copy(
            boardScroll = composed.board.offset,
            panelScrollOffsets = appState.panelScrollOffsets + composed.panels.mapValues { it.value.offset.y },
        )
    }

    // Render the initial frame before collecting any events. No previous frame, so every
    // scrollable follows its focus into view for the first time.
    val (initialBuffer, initialFrame) = composeFrame(appState, size.width, size.height, activeFlash, previous = null)
    renderer.render(initialBuffer)
    var frame = initialFrame
    syncScroll(frame)

    /**
     * Every subsequent render goes through here so that (a) the settled scroll positions are
     * synced back into appState before the next event is handled (see [syncScroll]) and (b) the
     * previous frame's focus rects are carried forward, which is what makes auto-follow fire on
     * focus movement only.
     *
     * [forgetFocus] drops that carry-forward so everything re-follows: used on resize, where the
     * viewport changes but content-space focus does not, and a shrink could otherwise strand the
     * cursor off-screen.
     */
    fun render(recenterBoard: Boolean = false, forgetFocus: Boolean = false) {
        val (buffer, next) = composeFrame(
            appState, size.width, size.height, activeFlash,
            previous = if (forgetFocus) null else frame,
            recenterBoard = recenterBoard,
        )
        renderer.render(buffer)
        frame = next
        syncScroll(frame)
    }

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

                    // Handle scroll events before any other input dispatch.
                    // Slot is computed first so overPanel can be passed to scrollDelta,
                    // which applies the Mordant posix wheel-parsing workaround (left/right
                    // press over a panel treated as wheel-up/down; see InputMapper.scrollDelta).
                    if (event is MouseEvent) {
                        val slot = frame.layout.slotAt(event.x, event.y)
                        val delta = InputMapper.scrollDelta(event, overPanel = slot != null)
                        if (delta != null) {
                            if (slot != null) {
                                val current = appState.panelScrollOffsets[slot.id] ?: 0
                                appState = appState.copy(
                                    panelScrollOffsets = appState.panelScrollOffsets + (slot.id to (current + delta)),
                                )
                            }
                            render()
                            return@collect  // scroll events never reach phases
                        }
                    }

                    val panel = (event as? KeyboardEvent)?.let(InputMapper::panelKey)?.let(PanelId::byKey)
                    if (panel != null) {
                        if (panel in PanelVisibility.visiblePanels(appState)) {
                            val current = appState.collapsedPanels
                            val next = if (panel in current) current - panel else current + panel
                            appState = appState.copy(collapsedPanels = next)
                        }
                        render()
                        return@collect
                    }

                    // Manual board panning: independent of game phase and, like scroll and
                    // panel-collapse above, still active once the match has ended.
                    val pan = (event as? KeyboardEvent)?.let(InputMapper::mapPanEvent)
                    if (pan != null) {
                        when (pan) {
                            is PanAction.Pan -> {
                                appState = appState.copy(boardScroll = appState.boardScroll + ScrollOffset(pan.dx, pan.dy))
                                render()
                            }
                            PanAction.Recenter -> render(recenterBoard = true)
                        }
                        return@collect
                    }

                    // Block game input (movement/attacks) once the match is over.
                    // Scroll, panel-collapse, and board panning are handled above and remain active.
                    // Only quit (handled by takeWhile) exits the loop.
                    if (appState.matchEnded != null) {
                        render()
                        return@collect
                    }

                    val transition = appState.phase.handle(event, appState) ?: run {
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
                    // Content-space focus doesn't change on resize, so without forgetting it a
                    // shrink could leave the cursor stranded outside the new viewport.
                    render(forgetFocus = true)
                }

                is UiEvent.FlashExpired -> {
                    if (ui.generation == flashGeneration) {
                        activeFlash = null
                        render()
                    }
                    // Stale expiry (earlier flash replaced by a newer one): ignore.
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

    // Cancel any pending flash job so the coroutineScope can complete cleanly.
    flashJob?.cancel()
}

private fun currentSize(terminal: Terminal): Size {
    val size = terminal.updateSize()
    check(size.width > 0) { "Terminal width must be positive, got: $size" }
    check(size.height > 0) { "Terminal height must be positive, got: $size" }
    return Size(size.width, size.height)
}
