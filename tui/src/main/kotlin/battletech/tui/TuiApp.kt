package battletech.tui

import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.HexCoordinates
import battletech.tactical.session.BattleSession
import battletech.tactical.session.TurnState
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.PanelVisibility
import battletech.tui.game.mapToTuiPhase
import battletech.tui.input.InputMapper
import battletech.tui.screen.ScreenBuffer
import battletech.tui.screen.ScreenRenderer
import battletech.tui.view.BoardView
import battletech.tui.view.Panel
import battletech.tui.view.PanelFrame
import battletech.tui.view.PanelSlot
import battletech.tui.view.Panels
import battletech.tui.view.StatusBarView
import battletech.tui.view.Viewport
import battletech.tui.view.resolvePanel
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal

public class TuiApp {

    public fun run() {
        val terminal = Terminal()
        val renderer = ScreenRenderer(terminal)

        val session = BattleSession(
            initialGameState = GameStateFactory().sampleGameState(),
            initialTurnState = TurnState.NULL,
        )

        // Kickstart cascades INITIATIVE → MOVEMENT; the session's gameLog
        // captures the cascade events and the LOG panel renders them.
        session.advance()
        var appState = AppState(
            session = session,
            phase = mapToTuiPhase(session.currentPhase),
            cursor = HexCoordinates(0, 0),
        )

        renderer.clear()

        try {
            terminal.enterRawMode(mouseTracking = MouseTracking.Normal).use { rawMode ->
                while (true) {
                    val currentSize = currentSize(terminal)

                    val transitionFlash: FlashMessage? = pendingFlash
                    pendingFlash = null
                    renderFrame(currentSize, renderer, appState, transitionFlash)

                    val event = rawMode.readEvent()
                    if (event is KeyboardEvent && InputMapper.isQuit(event)) break

                    val collapseIndex = if (event is KeyboardEvent) InputMapper.isCollapseToggle(event) else null
                    if (collapseIndex != null) {
                        val visible = PanelVisibility.visibleIndices(appState)
                        if (collapseIndex in visible) {
                            val current = appState.collapsedPanels
                            val next = if (collapseIndex in current) current - collapseIndex else current + collapseIndex
                            appState = appState.copy(collapsedPanels = next)
                        }
                        continue
                    }

                    val transition = appState.phase.handle(event, appState) ?: continue
                    appState = transition.app
                    pendingFlash = transition.flash
                }
            }
        } finally {
            renderer.cleanup()
        }
    }

    private var pendingFlash: FlashMessage? = null

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
    ) {
        val statusBarHeight = 7

        val visible = PanelVisibility.visibleIndices(appState)
        val frame = PanelFrame(appState)

        // 0 when hidden, the collapsed stub width when collapsed, else the
        // panel's own width. The board fills whatever is left.
        fun panelWidth(panel: Panel): Int = when {
            panel.id.index !in visible -> 0
            panel.id.index in appState.collapsedPanels -> 7
            else -> panel.width
        }

        val boardWidth = size.width - Panels.ordered.sumOf { panelWidth(it) }
        val boardHeight = size.height - statusBarHeight

        val buffer = ScreenBuffer(size.width, size.height)
        val viewport = Viewport(0, 0, boardWidth - 4, boardHeight - 4)

        val renderData = appState.phase.render(appState.gameState)
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
        boardView.render(buffer, 0, 0, boardWidth, boardHeight)

        var nextX = boardWidth
        for (panel in Panels.ordered) {
            val width = panelWidth(panel)
            if (width <= 0) continue
            val slot = PanelSlot(
                panel.id.index,
                width,
                panel.title,
                collapsed = panel.id.index in appState.collapsedPanels,
            ) { panel.build(frame) }
            resolvePanel(slot)?.render(buffer, nextX, 0, width, boardHeight)
            nextX += width
        }

        val prompt = flash?.text ?: appState.phase.prompt(appState)
        val activePlayerInfo = appState.phase.activePlayerLabel(appState)
        val statusBarView = StatusBarView(appState.currentPhase, prompt, activePlayerInfo)
        statusBarView.render(buffer, 0, boardHeight, size.width, statusBarHeight)

        renderer.render(buffer)
    }
}
