package battletech.tui

import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.BattleSession
import battletech.tactical.session.TurnState
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.mapToTuiPhase
import battletech.tui.input.InputMapper
import battletech.tui.screen.ScreenBuffer
import battletech.tui.screen.ScreenRenderer
import battletech.tui.view.BoardView
import battletech.tui.view.DeclaredTargetsView
import battletech.tui.view.LogView
import battletech.tui.view.SidebarView
import battletech.tui.view.StatusBarView
import battletech.tui.view.TargetStatusView
import battletech.tui.view.TargetsView
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
        val sidebarWidth = 28
        val logWidth = 28
        val statusBarHeight = 7

        val attackRender = appState.phase.attackRender(appState.gameState)
        val hasTargets = attackRender?.targets?.isNotEmpty() == true
        val targetsWidth = if (hasTargets) 28 else 0
        val isAttackPhase = appState.currentPhase == TurnPhase.WEAPON_ATTACK ||
            appState.currentPhase == TurnPhase.PHYSICAL_ATTACK
        val declaredWidth = if (isAttackPhase) 28 else 0
        val targetStatusUnit = appState.phase.targetStatusUnit(appState.gameState)
        val targetStatusWidth = if (targetStatusUnit != null) 28 else 0
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
        if (targetStatusUnit != null) {
            TargetStatusView(targetStatusUnit).render(buffer, nextX, 0, targetStatusWidth, boardHeight)
            nextX += targetStatusWidth
        }
        if (attackRender != null && hasTargets) {
            val targetsView = TargetsView(
                targets = attackRender.targets,
                weaponAssignments = attackRender.weaponAssignments,
                primaryTargetId = attackRender.primaryTargetId,
                cursorTargetIndex = attackRender.cursorTargetIndex,
                cursorWeaponIndex = attackRender.cursorWeaponIndex,
            )
            targetsView.render(buffer, nextX, 0, targetsWidth, boardHeight)
            nextX += targetsWidth
        }

        if (isAttackPhase) {
            val turnState = appState.turnState
            val viewingPlayer = if (turnState.attackSequence.order.isEmpty() || turnState.allAttackImpulsesComplete)
                PlayerId.PLAYER_1
            else
                turnState.activeAttackPlayer
            val declaredData = appState.phase.declaredTargetsRender(appState.gameState, turnState, viewingPlayer)
            if (declaredData != null) {
                DeclaredTargetsView(declaredData).render(buffer, nextX, 0, declaredWidth, boardHeight)
            }
            nextX += declaredWidth
        }

        val sidebarView = SidebarView(unit = selectedUnit)
        sidebarView.render(buffer, nextX, 0, sidebarWidth, boardHeight)
        nextX += sidebarWidth

        val logView = LogView(entries = appState.session.gameLog.snapshot(), gameState = appState.session.gameState)
        logView.render(buffer, nextX, 0, logWidth, boardHeight)

        val prompt = flash?.text ?: appState.phase.prompt(appState)
        val activePlayerInfo = appState.phase.activePlayerLabel(appState)
        val statusBarView = StatusBarView(appState.currentPhase, prompt, activePlayerInfo)
        statusBarView.render(buffer, 0, boardHeight, size.width, statusBarHeight)

        renderer.render(buffer)
    }
}
