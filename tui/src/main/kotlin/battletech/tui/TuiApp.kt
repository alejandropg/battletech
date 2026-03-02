package battletech.tui

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.definition.FireWeaponActionDefinition
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
import battletech.tui.game.AttackPhaseState
import battletech.tui.game.FlashMessage
import battletech.tui.game.IdlePhaseState
import battletech.tui.game.MovementController
import battletech.tui.game.MovementPhaseState
import battletech.tui.game.PhaseManager
import battletech.tui.game.autoAdvanceGlobalPhases
import battletech.tui.game.extractRenderData
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
            gameState = sampleGameState(),
            currentPhase = TurnPhase.INITIATIVE,
            cursor = HexCoordinates(0, 0),
            phase = IdlePhaseState(),
        )

        renderer.clear()

        try {
            terminal.enterRawMode(mouseTracking = MouseTracking.Normal).use { rawMode ->
                while (true) {
                    val currentSize = currentSize(terminal)

                    // Auto-advance global phases (Initiative, Heat, End, attack phase init)
                    val (advancedState, flash) = autoAdvanceGlobalPhases(appState, phaseManager)
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
        val width = if (size.width > 0) size.width else 80
        val height = if (size.height > 0) size.height else 24
        return Size(width, height)
    }

    private fun isAttackPhase(phase: TurnPhase): Boolean =
        phase == TurnPhase.WEAPON_ATTACK || phase == TurnPhase.PHYSICAL_ATTACK

    private fun renderFrame(
        size: Size,
        renderer: ScreenRenderer,
        appState: AppState,
        flash: FlashMessage? = null,
    ) {
        val sidebarWidth = 28
        val statusBarHeight = 7
        val attackPhase = appState.phase as? AttackPhaseState

        val hasTargets = attackPhase?.targets?.isNotEmpty() == true
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

        if (attackPhase != null && attackPhase.targets.isNotEmpty()) {
            val targetsView = TargetsView(
                targets = attackPhase.targets,
                weaponAssignments = attackPhase.weaponAssignments,
                primaryTargetId = attackPhase.primaryTargetId,
                cursorTargetIndex = attackPhase.cursorTargetIndex,
                cursorWeaponIndex = attackPhase.cursorWeaponIndex,
            )
            targetsView.render(buffer, boardWidth, 0, targetsWidth, boardHeight)
        }

        val sidebarView = SidebarView(unit = selectedUnit)
        sidebarView.render(buffer, boardWidth + targetsWidth, 0, sidebarWidth, boardHeight)

        val prompt = if (flash != null) flash.text else appState.phase.prompt
        val activePlayerInfo = if (appState.turnState != null) {
            val isMovement = appState.currentPhase == TurnPhase.MOVEMENT && !appState.turnState.allImpulsesComplete
            val isAttack = isAttackPhase(appState.currentPhase) &&
                    appState.turnState.attackOrder.isNotEmpty() && !appState.turnState.allAttackImpulsesComplete
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
            MechModels["AS7-D"].createUnit(
                id = UnitId("atlas"),
                owner = PlayerId.PLAYER_1,
                position = HexCoordinates(1, 1),
                facing = HexDirection.SE
            ),
            MechModels["HBK-4G"].createUnit(
                id = UnitId("hunchback"),
                owner = PlayerId.PLAYER_1,
                position = HexCoordinates(2, 3)
            ),
            MechModels["WVR-6R"].createUnit(
                id = UnitId("wolverine-1"),
                owner = PlayerId.PLAYER_2,
                pilotingSkill = 4,
                position = HexCoordinates(7, 3)
            ),
            MechModels["WVR-6R"].createUnit(
                id = UnitId("wolverine-2"),
                owner = PlayerId.PLAYER_2,
                position = HexCoordinates(8, 5)
            ),
        )

        return GameState(units, GameMap(hexes))
    }
}
