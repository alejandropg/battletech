package battletech.tui

import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
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
import battletech.tui.view.CollapsedPanelView
import battletech.tui.view.DeclaredTargetsView
import battletech.tui.view.LogView
import battletech.tui.view.SidebarView
import battletech.tui.view.StatusBarView
import battletech.tui.view.TargetStatusView
import battletech.tui.view.TargetsView
import battletech.tui.view.View
import battletech.tui.view.Viewport
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

        val attackRender = appState.phase.attackRender(appState.gameState)
        val targetStatusUnit = appState.phase.targetStatusUnit(appState.gameState)
        val visible = PanelVisibility.visibleIndices(appState)

        fun panelWidth(index: Int): Int = when {
            index !in visible -> 0
            index in appState.collapsedPanels -> 7
            else -> 28
        }

        val targetStatusWidth = panelWidth(TargetStatusView.INDEX)
        val targetsWidth      = panelWidth(TargetsView.INDEX)
        val declaredWidth     = panelWidth(DeclaredTargetsView.INDEX)
        val sidebarWidth      = panelWidth(SidebarView.INDEX)
        val logWidth          = panelWidth(LogView.INDEX)
        val boardWidth = size.width - sidebarWidth - logWidth - targetsWidth - declaredWidth - targetStatusWidth
        val boardHeight = size.height - statusBarHeight

        val buffer = ScreenBuffer(size.width, size.height)
        val viewport = Viewport(0, 0, boardWidth - 4, boardHeight - 4)

        val renderData = appState.phase.render(appState.gameState)
        val selectedUnit = appState.phase.selectedUnit(appState)
        val pathDestination = appState.phase.pathDestination()
        val movementMode = appState.phase.movementMode()

        val boardView = BoardView(
            appState.gameState,
            viewport,
            cursorPosition = appState.cursor,
            hexHighlights = renderData.hexHighlights,
            reachableFacings = renderData.reachableFacings,
            facingSelectionFacings = renderData.facingSelection?.facings,
            pathDestination = pathDestination,
            movementMode = movementMode,
            torsoFacings = renderData.torsoFacings,
            validTargetPositions = renderData.validTargetPositions,
            selectedTargetPosition = renderData.selectedTargetPosition,
        )
        boardView.render(buffer, 0, 0, boardWidth, boardHeight)

        var nextX = boardWidth
        if (targetStatusWidth > 0) {
            val v: View = if (TargetStatusView.INDEX in appState.collapsedPanels)
                CollapsedPanelView(TargetStatusView.INDEX, TargetStatusView.TITLE)
            else TargetStatusView(targetStatusUnit)
            v.render(buffer, nextX, 0, targetStatusWidth, boardHeight)
            nextX += targetStatusWidth
        }
        if (targetsWidth > 0) {
            val v: View = if (TargetsView.INDEX in appState.collapsedPanels)
                CollapsedPanelView(TargetsView.INDEX, TargetsView.TITLE)
            else TargetsView(
                targets = attackRender!!.targets,
                weaponAssignments = attackRender.weaponAssignments,
                primaryTargetId = attackRender.primaryTargetId,
                cursorTargetIndex = attackRender.cursorTargetIndex,
                cursorWeaponIndex = attackRender.cursorWeaponIndex,
            )
            v.render(buffer, nextX, 0, targetsWidth, boardHeight)
            nextX += targetsWidth
        }
        if (declaredWidth > 0) {
            if (DeclaredTargetsView.INDEX in appState.collapsedPanels) {
                CollapsedPanelView(DeclaredTargetsView.INDEX, DeclaredTargetsView.TITLE)
                    .render(buffer, nextX, 0, declaredWidth, boardHeight)
            } else {
                val turnState = appState.turnState
                val viewingPlayer = if (turnState.attackSequence.order.isEmpty() || turnState.allAttackImpulsesComplete)
                    PlayerId.PLAYER_1
                else
                    turnState.activeAttackPlayer
                val declaredData = appState.phase.declaredTargetsRender(appState.gameState, turnState, viewingPlayer)
                if (declaredData != null) {
                    DeclaredTargetsView(declaredData).render(buffer, nextX, 0, declaredWidth, boardHeight)
                }
            }
            nextX += declaredWidth
        }
        if (sidebarWidth > 0) {
            val v: View = if (SidebarView.INDEX in appState.collapsedPanels)
                CollapsedPanelView(SidebarView.INDEX, SidebarView.TITLE)
            else SidebarView(unit = selectedUnit)
            v.render(buffer, nextX, 0, sidebarWidth, boardHeight)
            nextX += sidebarWidth
        }
        if (logWidth > 0) {
            val v: View = if (LogView.INDEX in appState.collapsedPanels)
                CollapsedPanelView(LogView.INDEX, LogView.TITLE)
            else LogView(entries = appState.session.gameLog.snapshot(), gameState = appState.session.gameState)
            v.render(buffer, nextX, 0, logWidth, boardHeight)
        }

        val prompt = flash?.text ?: appState.phase.prompt(appState)
        val activePlayerInfo = appState.phase.activePlayerLabel(appState)
        val statusBarView = StatusBarView(appState.currentPhase, prompt, activePlayerInfo)
        statusBarView.render(buffer, 0, boardHeight, size.width, statusBarHeight)

        renderer.render(buffer)
    }
}
