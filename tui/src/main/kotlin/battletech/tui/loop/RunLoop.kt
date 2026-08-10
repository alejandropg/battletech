package battletech.tui.loop

import battletech.tactical.model.MatchOutcome
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.MatchEnded
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.PanelId
import battletech.tui.game.PanelScroll
import battletech.tui.game.PanelVisibility
import battletech.tui.game.mapToTuiPhase
import battletech.tui.input.InputMapper
import battletech.tui.input.KeyGlyph
import battletech.tui.input.PanAction
import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.FocusRect
import battletech.tui.screen.ScreenBuffer
import battletech.tui.screen.ScreenRenderer
import battletech.tui.view.BoardView
import battletech.tui.view.ContentExtent
import battletech.tui.view.FrameLayout
import battletech.tui.view.PanelFrame
import battletech.tui.view.PanelMetrics
import battletech.tui.view.PanelSlot
import battletech.tui.view.Panels
import battletech.tui.view.ScrollOffset
import battletech.tui.view.ScrollableView
import battletech.tui.view.StatusBarView
import battletech.tui.view.resolvePanel
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

private val WHITE_STYLE = Cell.Style(Color.WHITE)

/**
 * What the last render settled on. [boardFocus] and [panelFocus] are fed back into the next
 * render as each scrollable's `previousFocus`: [ScrollableView] auto-follows only when the focus
 * has actually moved, which is what lets a manual pan or wheel-scroll survive subsequent renders.
 */
private data class RenderedFrame(
    val layout: FrameLayout,
    val maxOffsets: Map<PanelId, Int>,
    val panelOffsets: Map<PanelId, Int>,
    val boardScroll: ScrollOffset,
    val boardFocus: FocusRect?,
    val panelFocus: Map<PanelId, FocusRect?>,
)

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

    // Render the initial frame before collecting any events. No previous frame, so every
    // scrollable follows its focus into view for the first time.
    var frame = renderFrame(size, renderer, appState, activeFlash, previous = null)
    appState = appState.copy(boardScroll = frame.boardScroll)

    /**
     * Every subsequent render goes through here so that (a) the board's effective scroll is
     * synced back into appState before the next event is handled — otherwise a click right after
     * a pan/follow would hit-test against a stale offset — and (b) the previous frame's focus
     * rects are carried forward, which is what makes auto-follow fire on focus movement only.
     *
     * [forgetFocus] drops that carry-forward so everything re-follows: used on resize, where the
     * viewport changes but content-space focus does not, and a shrink could otherwise strand the
     * cursor off-screen.
     */
    fun render(recenterBoard: Boolean = false, forgetFocus: Boolean = false) {
        frame = renderFrame(
            size, renderer, appState, activeFlash,
            previous = if (forgetFocus) null else frame,
            recenterBoard = recenterBoard,
        )
        appState = appState.copy(boardScroll = frame.boardScroll)
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
                        val slot = PanelScroll.slotAt(frame.layout, event.x, event.y)
                        val delta = InputMapper.scrollDelta(event, overPanel = slot != null)
                        if (delta != null) {
                            if (slot != null) {
                                val panel = Panels.byId.getValue(slot.panelKey)
                                appState = appState.copy(
                                    panelScrollOffsets = PanelScroll.update(
                                        appState.panelScrollOffsets,
                                        slot.panelKey,
                                        delta,
                                        frame.panelOffsets[slot.panelKey] ?: 0,
                                        frame.maxOffsets[slot.panelKey] ?: 0,
                                        panel.anchorBottom,
                                    ),
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

/**
 * Renders a centered overlay box in the board area declaring the match result.
 * Overlays board content — called after the board and panels are drawn so
 * it appears on top.
 */
private fun renderGameOverBanner(board: Canvas, outcome: MatchOutcome) {
    val winnerLine = when (outcome) {
        is MatchOutcome.Draw -> "Draw"
        is MatchOutcome.Victory -> "${playerName(outcome.winner)} wins!"
    }
    val bannerWidth = maxOf(winnerLine.length + 8, 24)
    val bannerHeight = 7
    if (bannerWidth > board.width || bannerHeight > board.height) return
    val banner = board.region(
        (board.width - bannerWidth) / 2, (board.height - bannerHeight) / 2,
        bannerWidth, bannerHeight,
    )
    banner.drawBox(
        title = "MATCH OVER",
        borderColor = Color.BRIGHT_YELLOW,
        titleColor = Color.BRIGHT_YELLOW,
    )
    val mx = (bannerWidth - winnerLine.length) / 2
    banner.writeString(mx, 3, winnerLine, WHITE_STYLE)
}

private fun playerName(player: PlayerId): String = when (player) {
    PlayerId.PLAYER_1 -> "P1"
    PlayerId.PLAYER_2 -> "P2"
}

private fun currentSize(terminal: Terminal): Size {
    val size = terminal.updateSize()
    check(size.width > 0) { "Terminal width must be positive, got: $size" }
    check(size.height > 0) { "Terminal height must be positive, got: $size" }
    return Size(size.width, size.height)
}

private fun renderFrame(
    size: Size,
    renderer: ScreenRenderer,
    appState: AppState,
    flash: FlashMessage? = null,
    previous: RenderedFrame?,
    recenterBoard: Boolean = false,
): RenderedFrame {
    val visible = PanelVisibility.visiblePanels(appState)
    val layout = FrameLayout.compute(
        termWidth = size.width,
        termHeight = size.height,
        visiblePanels = visible,
        collapsedPanels = appState.collapsedPanels,
        panelDescriptors = Panels.ordered.map {
            PanelMetrics(it.id, it.width, it.collapsedWidth)
        },
    )

    val buffer = ScreenBuffer(size.width, size.height)
    val screen = Canvas.of(buffer)
    val frame = PanelFrame(appState)

    val renderData = appState.phase.render(appState)
    val boardContent = BoardView(
        appState.visibleState,
        cursorPosition = appState.cursor,
        hexHighlights = renderData.hexHighlights,
        reachableFacings = renderData.reachableFacings,
        facingSelectionFacings = renderData.facingSelection?.facings,
        pathDestination = appState.phase.pathDestination(),
        movementMode = appState.phase.movementMode(),
        torsoFacings = renderData.torsoFacings,
        validTargetPositions = renderData.validTargetPositions,
        selectedTargetPosition = renderData.selectedTargetPosition,
    )
    val (mapWidth, mapHeight) = BoardView.contentSize(appState.visibleState.map)
    val boardScrollable = ScrollableView(
        title = "TACTICAL MAP",
        badge = null,
        content = boardContent,
        extent = ContentExtent.Fixed(mapWidth, mapHeight),
        offset = appState.boardScroll,
        previousFocus = previous?.boardFocus,
        recenter = recenterBoard,
    )
    val board = screen.region(0, layout.boardY, layout.boardWidth, layout.boardHeight)
    boardScrollable.render(board)

    val maxOffsets = mutableMapOf<PanelId, Int>()
    val panelOffsets = mutableMapOf<PanelId, Int>()
    val panelFocus = mutableMapOf<PanelId, FocusRect?>()
    for (slot in layout.slots) {
        val panel = Panels.byId.getValue(slot.panelKey)
        val panelSlot = PanelSlot(
            key = slot.panelKey,
            width = slot.width,
            title = panel.title,
            collapsed = slot.collapsed,
            scrollOffset = appState.panelScrollOffsets[slot.panelKey],
            anchorBottom = panel.anchorBottom,
            previousFocus = previous?.panelFocus?.get(slot.panelKey),
        ) { panel.build(frame) }
        val view = resolvePanel(panelSlot)
        view?.render(screen.region(slot.x, layout.boardY, slot.width, layout.boardHeight))
        if (view is ScrollableView) {
            maxOffsets[slot.panelKey] = view.state.maxOffset.y
            panelOffsets[slot.panelKey] = view.state.offset.y
            panelFocus[slot.panelKey] = view.state.focus
        }
    }

    val matchEnded = appState.matchEnded
    val prompt = when {
        matchEnded != null -> {
            val outcomeText = when (val outcome = matchEnded.outcome) {
                is MatchOutcome.Draw -> "Draw"
                is MatchOutcome.Victory -> "${playerName(outcome.winner)} wins!"
            }
            "Match over — $outcomeText  |  ${KeyGlyph.CTRL}c: quit"
        }
        flash != null -> flash.text
        else -> appState.phase.prompt(appState)
    }
    val activePlayerInfo = if (matchEnded != null) null else appState.phase.activePlayerLabel(appState)
    val statusBarView = StatusBarView(appState.currentPhase, prompt, activePlayerInfo)
    statusBarView.render(screen.region(0, 0, size.width, FrameLayout.STATUS_BAR_HEIGHT))

    if (matchEnded != null) {
        renderGameOverBanner(board, matchEnded.outcome)
    }

    renderer.render(buffer)
    return RenderedFrame(
        layout, maxOffsets, panelOffsets,
        boardScroll = boardScrollable.state.offset,
        boardFocus = boardScrollable.state.focus,
        panelFocus = panelFocus,
    )
}
