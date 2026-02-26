package battletech.tui

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.action.movement.MoveActionDefinition
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MechModels
import battletech.tactical.model.Terrain
import battletech.tactical.model.createUnit
import battletech.tui.game.AppState
import battletech.tui.game.AttackController
import battletech.tui.game.FlashMessage
import battletech.tui.game.MovementController
import battletech.tui.game.PhaseState
import battletech.tui.game.UnitSelectionResult
import battletech.tui.game.autoAdvanceGlobalPhases
import battletech.tui.game.extractRenderData
import battletech.tui.game.handlePhaseOutcome
import battletech.tui.game.moveCursor
import battletech.tui.game.selectableUnits
import battletech.tui.game.validateUnitSelection
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

    val movementController = MovementController(actionQueryService)
    val attackController = AttackController(actionQueryService)

    var appState = AppState(
        gameState = sampleGameState(),
        currentPhase = TurnPhase.INITIATIVE,
        cursor = HexCoordinates(0, 0),
        phaseState = PhaseState.Idle(),
    )

    renderer.clear()

    try {
        terminal.enterRawMode(mouseTracking = MouseTracking.Normal).use { rawMode ->
            while (true) {
                // Auto-advance global phases (Initiative, Heat, End)
                val (advancedState, flash) = autoAdvanceGlobalPhases(appState)
                if (flash != null) {
                    appState = advancedState
                    renderFrame(terminal, renderer, appState, flash)
                    continue
                }

                renderFrame(terminal, renderer, appState)

                val event = rawMode.readEvent()
                val action = when (event) {
                    is KeyboardEvent -> InputMapper.mapKeyboardEvent(event)
                    is MouseEvent -> InputMapper.mapMouseEvent(event, boardX = 2, boardY = 2)
                } ?: continue

                if (action is InputAction.Quit) break

                // Cursor movement — always handled here regardless of phase
                if (action is InputAction.MoveCursor) {
                    val newCursor = moveCursor(appState.cursor, action.direction, appState.gameState.map)
                    appState = appState.copy(cursor = newCursor)
                } else if (action is InputAction.ClickHex) {
                    val newCursor = action.coords
                    appState = appState.copy(cursor = newCursor)
                }

                // Phase-specific dispatch
                val phase = appState.phaseState
                appState = when (phase) {
                    is PhaseState.Idle -> handleIdle(action, appState, movementController, attackController)
                    is PhaseState.Movement -> handlePhaseOutcome(
                        movementController.handle(action, phase, appState.cursor, appState.gameState),
                        appState,
                    )

                    is PhaseState.Attack -> handlePhaseOutcome(
                        attackController.handle(action, phase, appState.gameState),
                        appState,
                    )
                }

                // Show flash from unit selection enforcement
                if (pendingFlash != null) {
                    renderFrame(terminal, renderer, appState, pendingFlash)
                    pendingFlash = null
                    continue
                }
            }
        }
    } finally {
        renderer.cleanup()
    }
}

private var pendingFlash: FlashMessage? = null

private fun trySelectUnit(
    appState: AppState,
    movementController: MovementController,
    attackController: AttackController,
): AppState {
    val unit = appState.gameState.unitAt(appState.cursor) ?: return appState
    val turnState = appState.turnState

    if (turnState != null && appState.currentPhase == TurnPhase.MOVEMENT) {
        when (validateUnitSelection(unit, turnState)) {
            UnitSelectionResult.NOT_YOUR_UNIT -> {
                pendingFlash = FlashMessage("Not your unit")
                return appState
            }
            UnitSelectionResult.ALREADY_MOVED -> {
                pendingFlash = FlashMessage("Already moved")
                return appState
            }
            UnitSelectionResult.VALID -> {}
        }
    }

    val newPhase = when (appState.currentPhase) {
        TurnPhase.MOVEMENT -> movementController.enter(unit, appState.gameState)
        TurnPhase.WEAPON_ATTACK -> attackController.enter(unit, TurnPhase.WEAPON_ATTACK, appState.gameState)
        TurnPhase.PHYSICAL_ATTACK -> attackController.enter(unit, TurnPhase.PHYSICAL_ATTACK, appState.gameState)
        else -> null
    }
    return if (newPhase != null) appState.copy(phaseState = newPhase) else appState
}

private fun handleIdle(
    action: InputAction,
    appState: AppState,
    movementController: MovementController,
    attackController: AttackController,
): AppState = when (action) {
    is InputAction.Confirm -> trySelectUnit(appState, movementController, attackController)

    is InputAction.ClickHex -> trySelectUnit(appState, movementController, attackController)

    is InputAction.CycleUnit -> {
        val turnState = appState.turnState
        val units = if (turnState != null && appState.currentPhase == TurnPhase.MOVEMENT) {
            selectableUnits(appState.gameState, turnState)
        } else {
            appState.gameState.units
        }
        if (units.isNotEmpty()) {
            val currentIdx = units.indexOfFirst { it.position == appState.cursor }
            val nextIdx = (currentIdx + 1) % units.size
            appState.copy(cursor = units[nextIdx].position)
        } else {
            appState
        }
    }

    else -> appState
}

private fun renderFrame(
    terminal: Terminal,
    renderer: ScreenRenderer,
    appState: AppState,
    flash: FlashMessage? = null,
) {
    val size = terminal.updateSize()
    val width = if (size.width > 0) size.width else 80
    val height = if (size.height > 0) size.height else 24
    val sidebarWidth = 28
    val statusBarHeight = 7
    val boardWidth = width - sidebarWidth
    val boardHeight = height - statusBarHeight

    val buffer = ScreenBuffer(width, height)
    val viewport = Viewport(0, 0, boardWidth - 4, boardHeight - 4)

    val renderData = extractRenderData(appState.phaseState)

    val selectedUnit = when (val phase = appState.phaseState) {
        is PhaseState.Movement -> appState.gameState.unitById(phase.unitId)
        is PhaseState.Attack -> appState.gameState.unitById(phase.unitId)
        is PhaseState.Idle -> appState.gameState.unitAt(appState.cursor)
    }

    val pathDestination = when (val phase = appState.phaseState) {
        is PhaseState.Movement.Browsing -> phase.hoveredPath?.lastOrNull()
        is PhaseState.Movement.SelectingFacing -> phase.path.lastOrNull()
        else -> null
    }

    val movementMode = (appState.phaseState as? PhaseState.Movement)?.reachability?.mode

    val boardView = BoardView(
        appState.gameState,
        viewport,
        cursorPosition = appState.cursor,
        hexHighlights = renderData.hexHighlights,
        reachableFacings = renderData.reachableFacings,
        facingSelectionHex = renderData.facingSelection?.hex,
        facingSelectionFacings = renderData.facingSelection?.facings,
        pathDestination = pathDestination,
        movementMode = movementMode,
    )
    boardView.render(buffer, 0, 0, boardWidth, boardHeight)

    val sidebarView = SidebarView(selectedUnit)
    sidebarView.render(buffer, boardWidth, 0, sidebarWidth, boardHeight)

    val prompt = if (flash != null) flash.text else appState.phaseState.prompt
    val activePlayerInfo = if (appState.turnState != null && !appState.turnState.allImpulsesComplete) {
        val playerName = if (appState.turnState.activePlayer == battletech.tactical.action.PlayerId.PLAYER_1) "Player 1" else "Player 2"
        playerName
    } else null
    val statusBarView = StatusBarView(appState.currentPhase, prompt, activePlayerInfo)
    statusBarView.render(buffer, 0, boardHeight, width, statusBarHeight)

    renderer.render(buffer)
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
        MechModels["AS7-D"].createUnit(id = UnitId("atlas"), owner = PlayerId.PLAYER_1, position = HexCoordinates(1, 1), facing = HexDirection.SE),
        MechModels["HBK-4G"].createUnit(id = UnitId("hunchback"), owner = PlayerId.PLAYER_1, position = HexCoordinates(2, 3)),
        MechModels["WVR-6R"].createUnit(id = UnitId("wolverine-1"), owner = PlayerId.PLAYER_2, pilotingSkill = 4, position = HexCoordinates(7, 3)),
        MechModels["WVR-6R"].createUnit(id = UnitId("wolverine-2"), owner = PlayerId.PLAYER_2, position = HexCoordinates(8, 5)),
    )

    return GameState(units, GameMap(hexes))
}
