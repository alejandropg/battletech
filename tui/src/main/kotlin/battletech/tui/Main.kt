package battletech.tui

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.movement.MoveActionDefinition
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MechModels
import battletech.tactical.model.Terrain
import battletech.tactical.model.createUnit
import battletech.tui.game.AttackPhaseController
import battletech.tui.game.EndPhaseController
import battletech.tui.game.GameLoop
import battletech.tui.game.GameLoopResult
import battletech.tui.game.HeatPhaseController
import battletech.tui.game.InitiativePhaseController
import battletech.tui.game.MovementPhaseController
import battletech.tui.game.PhaseControllerResult
import battletech.tui.game.PhaseState
import battletech.tui.input.CursorState
import battletech.tui.input.InputAction
import battletech.tui.input.InputMapper
import battletech.tui.screen.ScreenBuffer
import battletech.tui.screen.ScreenRenderer
import battletech.tui.view.BoardView
import battletech.tui.view.SidebarView
import battletech.tui.view.StatusBarView
import battletech.tui.view.Viewport
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal

public fun main() {
    val terminal = Terminal()
    val renderer = ScreenRenderer(terminal)
    val actionQueryService = ActionQueryService(listOf(MoveActionDefinition()), emptyList())

    val controllers = mapOf(
        TurnPhase.INITIATIVE to InitiativePhaseController() as battletech.tui.game.PhaseController,
        TurnPhase.MOVEMENT to MovementPhaseController(actionQueryService),
        TurnPhase.WEAPON_ATTACK to AttackPhaseController(actionQueryService, TurnPhase.WEAPON_ATTACK),
        TurnPhase.PHYSICAL_ATTACK to AttackPhaseController(actionQueryService, TurnPhase.PHYSICAL_ATTACK),
        TurnPhase.HEAT to HeatPhaseController(),
        TurnPhase.END to EndPhaseController(),
    )

    val gameLoop = GameLoop(sampleGameState(), TurnPhase.MOVEMENT)
    var cursor = CursorState(HexCoordinates(0, 0))
    var phaseState: PhaseState? = null

    renderer.clear()

    try {
        terminal.enterRawMode(mouseTracking = MouseTracking.Normal).use { rawMode ->
            var running = true
            while (running) {
                val size = terminal.updateSize()
                val width = if (size.width > 0) size.width else 80
                val height = if (size.height > 0) size.height else 24
                val sidebarWidth = 28
                val statusBarHeight = 7
                val boardWidth = width - sidebarWidth
                val boardHeight = height - statusBarHeight

                val buffer = ScreenBuffer(width, height)
                val viewport = Viewport(0, 0, boardWidth - 4, boardHeight - 4)

                val selectedUnit = phaseState?.selectedUnitId
                    ?.let { id -> gameLoop.gameState.units.find { it.id == id } }
                    ?: gameLoop.gameState.units.find { it.position == cursor.position }

                val highlights = phaseState?.hexHighlights() ?: emptyMap()
                val reachableFacings = phaseState?.facingsByPosition ?: emptyMap()
                val facingSelectionHex = phaseState?.facingSelectionHex
                val facingSelectionFacings = phaseState?.facingOptions
                    ?.map { it.facing }?.toSet()
                    ?.takeIf { facingSelectionHex != null }
                val boardView = BoardView(
                    gameLoop.gameState, viewport,
                    cursorPosition = cursor.position,
                    hexHighlights = highlights,
                    reachableFacings = reachableFacings,
                    facingSelectionHex = facingSelectionHex,
                    facingSelectionFacings = facingSelectionFacings,
                    pathDestination = phaseState?.highlightedPath?.lastOrNull(),
                )
                boardView.render(buffer, 0, 0, boardWidth, boardHeight)

                val sidebarView = SidebarView(selectedUnit)
                sidebarView.render(buffer, boardWidth, 0, sidebarWidth, boardHeight)

                val prompt = phaseState?.prompt ?: "Move cursor to select a hex"
                val statusBarView = StatusBarView(gameLoop.currentPhase, prompt)
                statusBarView.render(buffer, 0, boardHeight, width, statusBarHeight)

                renderer.render(buffer)

                val event = rawMode.readEvent()
                val action = when (event) {
                    is KeyboardEvent -> InputMapper.mapKeyboardEvent(event.key, event.ctrl, event.alt)
                    is MouseEvent -> InputMapper.mapMouseEvent(event, boardX = 2, boardY = 2)
                    else -> null
                }

                if (action == null) continue

                val loopResult = gameLoop.handleAction(action)
                if (loopResult is GameLoopResult.Quit) {
                    running = false
                    continue
                }

                when (action) {
                    is InputAction.MoveCursor -> {
                        cursor = cursor.moveCursor(action.direction, gameLoop.gameState.map)
                        val currentPhaseState = phaseState
                        if (currentPhaseState != null) {
                            val controller = controllers[gameLoop.currentPhase]
                            if (controller is MovementPhaseController) {
                                phaseState = controller.updatePathForCursor(cursor.position, currentPhaseState)
                            }
                        }
                    }
                    is InputAction.ClickHex -> {
                        cursor = CursorState(action.coords, cursor.selectedUnitId)
                        val currentPhaseState = phaseState
                        if (currentPhaseState != null) {
                            val controller = controllers[gameLoop.currentPhase]!!
                            val result = controller.handleAction(action, currentPhaseState, gameLoop.gameState)
                            when (result) {
                                is PhaseControllerResult.UpdateState -> phaseState = result.phaseState
                                is PhaseControllerResult.Complete -> {
                                    gameLoop.gameState = result.updatedGameState
                                    gameLoop.advancePhase()
                                    phaseState = null
                                }
                                is PhaseControllerResult.Cancelled -> phaseState = null
                            }
                        }
                    }
                    is InputAction.Confirm -> {
                        val currentPhaseState = phaseState
                        if (currentPhaseState != null) {
                            val controller = controllers[gameLoop.currentPhase]!!
                            val result = controller.handleAction(action, currentPhaseState, gameLoop.gameState)
                            when (result) {
                                is PhaseControllerResult.UpdateState -> phaseState = result.phaseState
                                is PhaseControllerResult.Complete -> {
                                    gameLoop.gameState = result.updatedGameState
                                    gameLoop.advancePhase()
                                    phaseState = null
                                }
                                is PhaseControllerResult.Cancelled -> phaseState = null
                            }
                        } else {
                            // Select unit and enter phase
                            val unit = gameLoop.gameState.units.find { it.position == cursor.position }
                            if (unit != null) {
                                val controller = controllers[gameLoop.currentPhase]!!
                                phaseState = controller.enter(unit, gameLoop.gameState)
                            }
                        }
                    }
                    is InputAction.Cancel -> {
                        val currentPhaseState = phaseState
                        if (currentPhaseState != null) {
                            val controller = controllers[gameLoop.currentPhase]!!
                            val result = controller.handleAction(action, currentPhaseState, gameLoop.gameState)
                            when (result) {
                                is PhaseControllerResult.UpdateState -> phaseState = result.phaseState
                                is PhaseControllerResult.Cancelled -> phaseState = null
                                is PhaseControllerResult.Complete -> {
                                    gameLoop.gameState = result.updatedGameState
                                    gameLoop.advancePhase()
                                    phaseState = null
                                }
                            }
                        }
                    }
                    is InputAction.SelectAction -> {
                        val currentPhaseState = phaseState
                        if (currentPhaseState != null) {
                            val controller = controllers[gameLoop.currentPhase]!!
                            val result = controller.handleAction(action, currentPhaseState, gameLoop.gameState)
                            when (result) {
                                is PhaseControllerResult.UpdateState -> phaseState = result.phaseState
                                is PhaseControllerResult.Complete -> {
                                    gameLoop.gameState = result.updatedGameState
                                    gameLoop.advancePhase()
                                    phaseState = null
                                }
                                is PhaseControllerResult.Cancelled -> phaseState = null
                            }
                        }
                    }
                    is InputAction.CycleUnit -> {
                        val currentPhaseState = phaseState
                        val controller = controllers[gameLoop.currentPhase]
                        if (currentPhaseState != null && controller is MovementPhaseController) {
                            val unitName = gameLoop.gameState.units
                                .find { it.id == currentPhaseState.selectedUnitId }?.name ?: ""
                            phaseState = controller.cycleMode(currentPhaseState, unitName)
                        } else {
                            val units = gameLoop.gameState.units
                            if (units.isNotEmpty()) {
                                val currentIdx = units.indexOfFirst { it.position == cursor.position }
                                val nextIdx = (currentIdx + 1) % units.size
                                cursor = CursorState(units[nextIdx].position, units[nextIdx].id)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    } finally {
        renderer.cleanup()
    }
}

private fun sampleGameState(): GameState {
    val hexes = mutableMapOf<HexCoordinates, Hex>()
    for (col in 0..9) {
        for (row in 0..9) {
            val coords = HexCoordinates(col, row)
            val terrain = when {
                col == 3 && row in 2..5 -> Terrain.LIGHT_WOODS
                col == 4 && row in 3..4 -> Terrain.HEAVY_WOODS
                col == 6 && row in 1..3 -> Terrain.WATER
                else -> Terrain.CLEAR
            }
            val elevation = if (col == 5 && row in 2..4) 2 else 0
            hexes[coords] = Hex(coords, terrain, elevation)
        }
    }

    val units = listOf(
        MechModels["AS7-D"].createUnit(id = UnitId("atlas"), position = HexCoordinates(1, 1)),
        MechModels["HBK-4G"].createUnit(id = UnitId("hunchback"), position = HexCoordinates(7, 3)),
        MechModels["WVR-6R"].createUnit(id = UnitId("wolverine-1"), pilotingSkill = 4, position = HexCoordinates(4, 7)),
    )

    return GameState(units = units, map = GameMap(hexes))
}
