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
import battletech.tui.view.FrameLayout
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

        for (slot in layout.slots) {
            val panel = Panels.ordered.first { it.id.index == slot.panelIndex }
            val panelSlot = PanelSlot(slot.panelIndex, slot.width, panel.title, slot.collapsed) { panel.build(frame) }
            resolvePanel(panelSlot)?.render(buffer, slot.x, 0, slot.width, layout.boardHeight)
        }

        val prompt = flash?.text ?: appState.phase.prompt(appState)
        val activePlayerInfo = appState.phase.activePlayerLabel(appState)
        val statusBarView = StatusBarView(appState.currentPhase, prompt, activePlayerInfo)
        statusBarView.render(buffer, 0, layout.boardHeight, size.width, FrameLayout.STATUS_BAR_HEIGHT)

        renderer.render(buffer)
    }
}
