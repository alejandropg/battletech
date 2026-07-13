package battletech.tui

import battletech.tactical.model.GameState
import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.BattleSession
import battletech.tactical.session.GameSession
import battletech.tactical.session.MatchEnded
import battletech.tactical.session.TurnState
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.PanelScroll
import battletech.tui.game.PanelVisibility
import battletech.tui.game.mapToTuiPhase
import battletech.tui.input.InputMapper
import battletech.tui.loop.UiEvent
import battletech.tui.loop.resizeEvents
import battletech.tui.loop.terminalInputEvents
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import battletech.tui.screen.ScreenRenderer
import battletech.tui.view.BoardView
import battletech.tui.view.FrameLayout
import battletech.tui.view.PanelFrame
import battletech.tui.view.PanelSlot
import battletech.tui.view.Panels
import battletech.tui.view.ScrollablePanelView
import battletech.tui.view.StatusBarView
import battletech.tui.view.Viewport
import battletech.tui.view.resolvePanel
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

public class TuiApp(
    private val providedSession: GameSession? = null,
    private val localPlayer: PlayerId? = null,
    private val initialGameState: GameState? = null,
) {

    private data class RenderedFrame(
        val layout: FrameLayout,
        val maxOffsets: Map<Int, Int>,
    )

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
                val subscriptions = PlayerId.entries.map { player ->
                    session.subscribe(player) { internalEvents.trySend(UiEvent.Session(it)) }
                }
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
                    subscriptions.forEach { it.unsubscribe() }
                }
            }
        } finally {
            renderer.cleanup()
        }
    }

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

        var frame = RenderedFrame(
            layout = FrameLayout(boardWidth = 0, boardHeight = 0, slots = emptyList()),
            maxOffsets = emptyMap(),
        )

        // Render the initial frame before collecting any events.
        frame = renderFrame(size, renderer, appState, activeFlash)

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
                                    val panel = Panels.ordered.first { it.id.index == slot.panelIndex }
                                    appState = appState.copy(
                                        panelScrollOffsets = PanelScroll.update(
                                            appState.panelScrollOffsets,
                                            slot.panelIndex,
                                            delta,
                                            frame.maxOffsets[slot.panelIndex] ?: 0,
                                            panel.anchorBottom,
                                        ),
                                    )
                                }
                                frame = renderFrame(size, renderer, appState, activeFlash)
                                return@collect  // scroll events never reach phases
                            }
                        }

                        val collapseIndex = if (event is KeyboardEvent) {
                            InputMapper.isCollapseToggle(event)
                        } else null
                        if (collapseIndex != null) {
                            val visible = PanelVisibility.visibleIndices(appState)
                            if (collapseIndex in visible) {
                                val current = appState.collapsedPanels
                                val next = if (collapseIndex in current) current - collapseIndex else current + collapseIndex
                                appState = appState.copy(collapsedPanels = next)
                            }
                            frame = renderFrame(size, renderer, appState, activeFlash)
                            return@collect
                        }

                        // Block game input (movement/attacks) once the match is over.
                        // Scroll and panel-collapse are handled above and remain active.
                        // Only quit (handled by takeWhile) exits the loop.
                        if (appState.matchEnded != null) {
                            frame = renderFrame(size, renderer, appState, activeFlash)
                            return@collect
                        }

                        val transition = appState.phase.handle(event, appState) ?: run {
                            frame = renderFrame(size, renderer, appState, activeFlash)
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

                        frame = renderFrame(size, renderer, appState, activeFlash)
                    }

                    is UiEvent.Resized -> {
                        size = ui.size
                        frame = renderFrame(size, renderer, appState, activeFlash)
                    }

                    is UiEvent.FlashExpired -> {
                        if (ui.generation == flashGeneration) {
                            activeFlash = null
                            frame = renderFrame(size, renderer, appState, activeFlash)
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
                        val resynced = mapToTuiPhase(appState.session.currentPhase)
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
                        frame = renderFrame(size, renderer, appState, activeFlash)
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
    private fun renderGameOverBanner(
        buffer: ScreenBuffer,
        boardWidth: Int,
        boardHeight: Int,
        winner: PlayerId?,
    ) {
        val winnerLine = if (winner == null) "Draw" else "${playerName(winner)} wins!"
        val bannerWidth = maxOf(winnerLine.length + 8, 24)
        val bannerHeight = 7
        val bx = (boardWidth - bannerWidth) / 2
        val by = (boardHeight - bannerHeight) / 2
        if (bx < 0 || by < 0 || bx + bannerWidth > buffer.width || by + bannerHeight > buffer.height) return
        buffer.drawBox(
            bx, by, bannerWidth, bannerHeight,
            title = "MATCH OVER",
            borderColor = Color.BRIGHT_YELLOW,
            titleColor = Color.BRIGHT_YELLOW,
        )
        val mx = bx + (bannerWidth - winnerLine.length) / 2
        buffer.writeString(mx, by + 3, winnerLine, Color.WHITE)
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
    ): RenderedFrame {
        val visible = PanelVisibility.visibleIndices(appState)
        val layout = FrameLayout.compute(
            termWidth = size.width,
            termHeight = size.height,
            visiblePanels = visible,
            collapsedPanels = appState.collapsedPanels,
            panelDescriptors = Panels.ordered.map { it.id.index to it.width },
        )

        val buffer = ScreenBuffer(size.width, size.height)
        val frame = PanelFrame(appState)

        val renderData = appState.phase.render(appState.gameState)
        val viewport = Viewport(0, 0, layout.boardWidth - 4, layout.boardHeight - 4)
        val boardView = BoardView(
            appState.gameState,
            viewport,
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
        boardView.render(buffer, 0, 0, layout.boardWidth, layout.boardHeight)

        val maxOffsets = mutableMapOf<Int, Int>()
        for (slot in layout.slots) {
            val panel = Panels.ordered.first { it.id.index == slot.panelIndex }
            val panelSlot = PanelSlot(
                index = slot.panelIndex,
                width = slot.width,
                title = panel.title,
                collapsed = slot.collapsed,
                scrollOffset = appState.panelScrollOffsets[slot.panelIndex],
                anchorBottom = panel.anchorBottom,
            ) { panel.build(frame) }
            val view = resolvePanel(panelSlot)
            view?.render(buffer, slot.x, 0, slot.width, layout.boardHeight)
            val mo = (view as? ScrollablePanelView)?.maxOffset
            if (mo != null) maxOffsets[slot.panelIndex] = mo
        }

        val matchEnded = appState.matchEnded
        val prompt = when {
            matchEnded != null -> {
                val winner = matchEnded.winner
                if (winner == null) "Match over — Draw  |  ctrl+c: quit"
                else "Match over — ${playerName(winner)} wins!  |  ctrl+c: quit"
            }
            flash != null -> flash.text
            else -> appState.phase.prompt(appState)
        }
        val activePlayerInfo = if (matchEnded != null) null else appState.phase.activePlayerLabel(appState)
        val statusBarView = StatusBarView(appState.currentPhase, prompt, activePlayerInfo)
        statusBarView.render(buffer, 0, layout.boardHeight, size.width, FrameLayout.STATUS_BAR_HEIGHT)

        if (matchEnded != null) {
            renderGameOverBanner(buffer, layout.boardWidth, layout.boardHeight, matchEnded.winner)
        }

        renderer.render(buffer)
        return RenderedFrame(layout, maxOffsets)
    }
}
