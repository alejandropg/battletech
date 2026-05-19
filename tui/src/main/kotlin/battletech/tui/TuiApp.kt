package battletech.tui

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.attack.definition.FireWeaponActionDefinition
import battletech.tactical.action.movement.MoveActionDefinition
import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.HexCoordinates
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tactical.session.TurnState
import battletech.tui.game.phase.InitiativePhase
import battletech.tui.game.phase.PhaseServices
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
        val services = PhaseServices(
            actionQueryService = ActionQueryService(
                MoveActionDefinition(),
                listOf(FireWeaponActionDefinition()),
            ),
        )

        var appState = AppState(
            GameStateFactory().sampleGameState(),
            TurnState.NULL,
            InitiativePhase,
            HexCoordinates(0, 0)
        )

        renderer.clear()

        try {
            terminal.enterRawMode(mouseTracking = MouseTracking.Normal).use { rawMode ->
                while (true) {
                    val currentSize = currentSize(terminal)

                    val tick = appState.phase.tick(appState, services)
                    if (tick != null) {
                        appState = tick.app
                        renderFrame(currentSize, renderer, appState, tick.flash)
                        continue
                    }

                    renderFrame(currentSize, renderer, appState)

                    val event = rawMode.readEvent()
                    if (event is KeyboardEvent && InputMapper.isQuit(event)) break

                    val transition = appState.phase.handle(event, appState, services) ?: continue

                    appState = transition.app
                    if (transition.flash != null) {
                        renderFrame(currentSize, renderer, appState, transition.flash)
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

        val attackRender = appState.phase.attackRender(appState.gameState)
        val hasTargets = attackRender?.targets?.isNotEmpty() == true
        val targetsWidth = if (hasTargets) 28 else 0
        val boardWidth = size.width - sidebarWidth - targetsWidth
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
        )
        boardView.render(buffer, 0, 0, boardWidth, boardHeight)

        if (attackRender != null && hasTargets) {
            val targetsView = TargetsView(
                targets = attackRender.targets,
                weaponAssignments = attackRender.weaponAssignments,
                primaryTargetId = attackRender.primaryTargetId,
                cursorTargetIndex = attackRender.cursorTargetIndex,
                cursorWeaponIndex = attackRender.cursorWeaponIndex,
            )
            targetsView.render(buffer, boardWidth, 0, targetsWidth, boardHeight)
        }

        val sidebarView = SidebarView(unit = selectedUnit)
        sidebarView.render(buffer, boardWidth + targetsWidth, 0, sidebarWidth, boardHeight)

        val prompt = flash?.text ?: appState.phase.prompt(appState)
        val activePlayerInfo = appState.phase.activePlayerLabel(appState)
        val statusBarView = StatusBarView(appState.currentPhase, prompt, activePlayerInfo)
        statusBarView.render(buffer, 0, boardHeight, size.width, statusBarHeight)

        renderer.render(buffer)
    }

}
