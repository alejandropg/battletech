package battletech.tui

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.attack.definition.FireWeaponActionDefinition
import battletech.tactical.action.movement.MoveActionDefinition
import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.HexCoordinates
import battletech.tui.game.AppState
import battletech.tui.game.AttackController
import battletech.tui.game.AttackPhaseState
import battletech.tui.game.FlashMessage
import battletech.tui.game.IdlePhaseState
import battletech.tui.game.MovementController
import battletech.tui.game.MovementPhaseState
import battletech.tui.game.PhaseManager
import battletech.tui.game.extractRenderData
import battletech.tui.game.isAttack
import battletech.tui.game.phasePrompt
import battletech.tui.game.targetInfos
import battletech.tui.input.InputMapper
import battletech.tui.screen.ScreenBuffer
import battletech.tui.screen.ScreenRenderer
import battletech.tui.view.BoardView
import battletech.tui.view.SidebarView
import battletech.tui.view.StatusBarView
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
        val actionQueryService = ActionQueryService(
            MoveActionDefinition(),
            listOf(FireWeaponActionDefinition()),
        )

        val phaseManager = PhaseManager(
            movementController = MovementController(actionQueryService),
            attackController = AttackController(),
        )

        var appState = AppState(
            gameState = GameStateFactory().sampleGameState(),
            currentPhase = TurnPhase.INITIATIVE,
            cursor = HexCoordinates(0, 0),
            phase = IdlePhaseState,
        )

        renderer.clear()

        try {
            terminal.enterRawMode(mouseTracking = MouseTracking.Normal).use { rawMode ->
                while (true) {
                    val currentSize = currentSize(terminal)

                    // Auto-advance global phases (Initiative, Heat, End, attack phase init)
                    val (advancedState, flash) = phaseManager.advanceAutomaticPhases(appState)
                    if (flash != null) {
                        appState = advancedState
                        renderFrame(currentSize, renderer, appState, flash)
                        continue
                    }

                    renderFrame(currentSize, renderer, appState)

                    val event = rawMode.readEvent()
                    if (event is KeyboardEvent && InputMapper.isQuit(event)) break

                    // Polymorphic dispatch — no when by phase type
                    val result = appState.phase.processEvent(event, appState, phaseManager) ?: continue

                    appState = result.appState
                    if (result.flash != null) {
                        renderFrame(currentSize, renderer, appState, result.flash)
                        continue
                    }
                }
            }
        } finally {
            renderer.cleanup()
        }
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
    ) {
        val sidebarWidth = 28
        val statusBarHeight = 7
        val attackPhase = appState.phase as? AttackPhaseState
        val attackTargets = attackPhase?.let { phase ->
            val unit = appState.gameState.unitById(phase.unitId) ?: return@let emptyList()
            targetInfos(unit, phase.torsoFacing, appState.gameState)
        }.orEmpty()

        val hasTargets = attackTargets.isNotEmpty()
        val targetsWidth = if (hasTargets) 28 else 0
        val boardWidth = size.width - sidebarWidth - targetsWidth
        val boardHeight = size.height - statusBarHeight

        val buffer = ScreenBuffer(size.width, size.height)
        val viewport = Viewport(0, 0, boardWidth - 4, boardHeight - 4)

        val renderData = extractRenderData(appState.phase, appState.gameState)

        val selectedUnit = when (val phase = appState.phase) {
            is MovementPhaseState -> appState.gameState.unitById(phase.unitId)
            is AttackPhaseState -> appState.gameState.unitById(phase.unitId)
            is IdlePhaseState -> appState.gameState.unitAt(appState.cursor)
        }

        val pathDestination = when (val phase = appState.phase) {
            is MovementPhaseState.Browsing -> phase.hoveredPath?.lastOrNull()
            is MovementPhaseState.SelectingFacing -> phase.path.lastOrNull()
            else -> null
        }

        val movementMode = (appState.phase as? MovementPhaseState)?.reachability?.mode

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
        )
        boardView.render(buffer, 0, 0, boardWidth, boardHeight)

        if (attackPhase != null && attackTargets.isNotEmpty()) {
            val targetsView = TargetsView(
                targets = attackTargets,
                weaponAssignments = attackPhase.weaponAssignments,
                primaryTargetId = attackPhase.primaryTargetId,
                cursorTargetIndex = attackPhase.cursorTargetIndex,
                cursorWeaponIndex = attackPhase.cursorWeaponIndex,
            )
            targetsView.render(buffer, boardWidth, 0, targetsWidth, boardHeight)
        }

        val sidebarView = SidebarView(unit = selectedUnit)
        sidebarView.render(buffer, boardWidth + targetsWidth, 0, sidebarWidth, boardHeight)

        val prompt = if (flash != null) flash.text else phasePrompt(appState)
        val activePlayerInfo = if (appState.turnState != null) {
            val isMovement = appState.currentPhase == TurnPhase.MOVEMENT && !appState.turnState.allImpulsesComplete
            val isAttack = appState.currentPhase.isAttack &&
                    appState.turnState.attackSequence.order.isNotEmpty() && !appState.turnState.allAttackImpulsesComplete
            if (isMovement) {
                if (appState.turnState.activePlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
            } else if (isAttack) {
                if (appState.turnState.activeAttackPlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
            } else null
        } else null
        val statusBarView = StatusBarView(appState.currentPhase, prompt, activePlayerInfo)
        statusBarView.render(buffer, 0, boardHeight, size.width, statusBarHeight)

        renderer.render(buffer)
    }

}
