package battletech.tui

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.resolveAttacks
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
import battletech.tui.game.TurnState
import battletech.tui.game.UnitSelectionResult
import battletech.tui.game.advanceAfterUnitAttacked
import battletech.tui.game.attackPrompt
import battletech.tui.game.autoAdvanceGlobalPhases
import battletech.tui.game.extractRenderData
import battletech.tui.game.handlePhaseOutcome
import battletech.tui.game.moveCursor
import battletech.tui.game.nextPhase
import battletech.tui.game.selectableAttackUnits
import battletech.tui.game.selectableUnits
import battletech.tui.game.validateAttackUnitSelection
import battletech.tui.game.validateUnitSelection
import battletech.tui.input.InputAction
import battletech.tui.input.InputMapper
import battletech.tui.screen.ScreenBuffer
import battletech.tui.screen.ScreenRenderer
import battletech.tui.view.BoardView
import battletech.tui.view.SidebarView
import battletech.tui.view.StatusBarView
import battletech.tui.view.TargetsView
import battletech.tui.view.Viewport
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.random.Random

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
                // Auto-advance global phases (Initiative, Heat, End, attack phase init)
                val (advancedState, flash) = autoAdvanceGlobalPhases(appState)
                if (flash != null) {
                    appState = advancedState
                    // Initialize attack impulse when first entering an attack phase
                    val ts = advancedState.turnState
                    if (ts != null && !ts.allAttackImpulsesComplete &&
                        (advancedState.currentPhase == TurnPhase.WEAPON_ATTACK ||
                            advancedState.currentPhase == TurnPhase.PHYSICAL_ATTACK)
                    ) {
                        attackController.initializeImpulse(
                            ts.activeAttackPlayer,
                            ts.currentAttackImpulse.unitCount,
                        )
                        // Update prompt with declaration progress
                        appState = appState.copy(phaseState = PhaseState.Idle(buildAttackPrompt(ts, attackController)))
                    }
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

                // Cursor movement — controlled per-state
                val shouldMoveCursorOnArrow = when (appState.phaseState) {
                    is PhaseState.Attack.TorsoFacing -> false     // arrows twist torso
                    is PhaseState.Attack.WeaponAssignment -> false // arrows navigate weapons
                    else -> true
                }
                if (action is InputAction.MoveCursor && shouldMoveCursorOnArrow) {
                    val newCursor = moveCursor(appState.cursor, action.direction, appState.gameState.map)
                    appState = appState.copy(cursor = newCursor)
                }
                if (action is InputAction.ClickHex) {
                    appState = appState.copy(cursor = action.coords)
                }

                // Phase-specific dispatch
                val phase = appState.phaseState
                appState = when (phase) {
                    is PhaseState.Idle -> handleIdle(action, appState, movementController, attackController)
                    is PhaseState.Movement -> handlePhaseOutcome(
                        movementController.handle(action, phase, appState.cursor, appState.gameState),
                        appState,
                    )
                    is PhaseState.Attack -> {
                        val outcome = attackController.handle(action, phase, appState.cursor, appState.gameState)
                        val newState = handlePhaseOutcome(outcome, appState)
                        // After returning to Idle from attack, refresh prompt with declaration progress
                        if (newState.phaseState is PhaseState.Idle && isAttackPhase(newState.currentPhase)) {
                            val ts = newState.turnState
                            if (ts != null && !ts.allAttackImpulsesComplete) {
                                newState.copy(phaseState = PhaseState.Idle(buildAttackPrompt(ts, attackController)))
                            } else {
                                newState
                            }
                        } else {
                            newState
                        }
                    }
                }

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

private fun isAttackPhase(phase: TurnPhase) =
    phase == TurnPhase.WEAPON_ATTACK || phase == TurnPhase.PHYSICAL_ATTACK

private fun buildAttackPrompt(turnState: TurnState, attackController: AttackController): String =
    attackPrompt(turnState, attackController.declaredCount(), attackController.currentImpulseUnitCount())

private fun commitAttackImpulse(
    appState: AppState,
    attackController: AttackController,
    random: Random = Random,
): AppState {
    val turnState = appState.turnState ?: return appState
    val committedUnitIds = attackController.commitImpulse()

    var newTurnState = turnState
    for (unitId in committedUnitIds) {
        newTurnState = advanceAfterUnitAttacked(newTurnState, unitId)
    }

    return if (newTurnState.allAttackImpulsesComplete) {
        if (appState.currentPhase == TurnPhase.WEAPON_ATTACK) {
            // Resolve all weapon attacks and transition to physical attack
            val declarations = attackController.collectDeclarations()
            val resolvedGameState = if (declarations.isNotEmpty()) {
                val (resolved, results) = resolveAttacks(declarations, appState.gameState, random)
                val hitCount = results.count { it.hit }
                val totalDamage = results.sumOf { it.damageApplied }
                pendingFlash = FlashMessage(
                    "Attacks resolved: ${results.size} attacks, $hitCount hits, $totalDamage damage",
                )
                resolved
            } else {
                appState.gameState
            }
            attackController.clearDeclarations()

            val physicalTurnState = newTurnState.copy(
                attackedUnitIds = emptySet(),
                currentAttackImpulseIndex = 0,
                unitsAttackedInCurrentImpulse = 0,
                attackOrder = emptyList(),  // will be re-initialized by autoAdvanceGlobalPhases
            )
            appState.copy(
                gameState = resolvedGameState,
                currentPhase = nextPhase(TurnPhase.WEAPON_ATTACK),
                phaseState = PhaseState.Idle(),
                turnState = physicalTurnState,
            )
        } else {
            // Physical attack done — advance phase
            appState.copy(
                currentPhase = nextPhase(appState.currentPhase),
                phaseState = PhaseState.Idle(),
                turnState = newTurnState,
            )
        }
    } else {
        // More impulses remain — initialize the next one
        attackController.initializeImpulse(
            newTurnState.activeAttackPlayer,
            newTurnState.currentAttackImpulse.unitCount,
        )
        appState.copy(
            phaseState = PhaseState.Idle(buildAttackPrompt(newTurnState, attackController)),
            turnState = newTurnState,
        )
    }
}

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
            UnitSelectionResult.ALREADY_MOVED, UnitSelectionResult.ALREADY_ACTED -> {
                pendingFlash = FlashMessage("Already moved")
                return appState
            }
            UnitSelectionResult.VALID -> {}
        }
    }

    if (turnState != null && isAttackPhase(appState.currentPhase)) {
        when (validateAttackUnitSelection(unit, turnState)) {
            UnitSelectionResult.NOT_YOUR_UNIT -> {
                pendingFlash = FlashMessage("Not your unit")
                return appState
            }
            UnitSelectionResult.ALREADY_ACTED -> {
                pendingFlash = FlashMessage("Already committed attacks")
                return appState
            }
            UnitSelectionResult.ALREADY_MOVED, UnitSelectionResult.VALID -> {}
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

    is InputAction.CommitDeclarations -> {
        val turnState = appState.turnState
        if (turnState != null && isAttackPhase(appState.currentPhase)) {
            if (attackController.canCommit()) {
                commitAttackImpulse(appState, attackController)
            } else {
                val declared = attackController.declaredCount()
                val total = attackController.currentImpulseUnitCount()
                pendingFlash = FlashMessage("Declare all units first ($declared/$total declared)")
                appState
            }
        } else {
            appState
        }
    }

    is InputAction.CycleUnit -> {
        val turnState = appState.turnState
        val units = when {
            turnState != null && appState.currentPhase == TurnPhase.MOVEMENT ->
                selectableUnits(appState.gameState, turnState)
            turnState != null && isAttackPhase(appState.currentPhase) -> {
                // Tab cycles undeclared units first; all selectable units (not yet committed)
                val all = selectableAttackUnits(appState.gameState, turnState)
                val undeclared = all.filter { !attackController.isDeclared(it.id) }
                undeclared.ifEmpty { all }
            }
            else -> appState.gameState.units
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
    val attackPhase = appState.phaseState as? PhaseState.Attack

    val hasTargets = when (attackPhase) {
        is PhaseState.Attack.TorsoFacing -> attackPhase.targets.isNotEmpty()
        is PhaseState.Attack.TargetBrowsing -> true  // always show (at least "No Attack")
        is PhaseState.Attack.WeaponAssignment -> true
        null -> false
    }
    val targetsWidth = if (hasTargets) 22 else 0
    val boardWidth = width - sidebarWidth - targetsWidth
    val boardHeight = height - statusBarHeight

    val buffer = ScreenBuffer(width, height)
    val viewport = Viewport(0, 0, boardWidth - 4, boardHeight - 4)

    val renderData = extractRenderData(appState.phaseState, appState.gameState)

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
        torsoFacings = renderData.torsoFacings,
        validTargetPositions = renderData.validTargetPositions,
    )
    boardView.render(buffer, 0, 0, boardWidth, boardHeight)

    if (hasTargets) {
        val targetsView = when (attackPhase) {
            is PhaseState.Attack.TorsoFacing -> TargetsView(
                targets = attackPhase.targets,
                weaponAssignments = emptyMap(),
                primaryTargetId = null,
                selectedTargetIndex = -1,
                showWeapons = false,
                showNoAttack = false,
            )
            is PhaseState.Attack.TargetBrowsing -> TargetsView(
                targets = attackPhase.targets,
                weaponAssignments = attackPhase.weaponAssignments,
                primaryTargetId = attackPhase.primaryTargetId,
                selectedTargetIndex = attackPhase.selectedTargetIndex,
                showWeapons = false,
                showNoAttack = true,
            )
            is PhaseState.Attack.WeaponAssignment -> TargetsView(
                targets = attackPhase.targets,
                weaponAssignments = attackPhase.weaponAssignments,
                primaryTargetId = attackPhase.primaryTargetId,
                selectedTargetIndex = attackPhase.selectedTargetIndex,
                showWeapons = true,
                showNoAttack = false,
            )
            else -> null
        }
        targetsView?.render(buffer, boardWidth, 0, targetsWidth, boardHeight)
    }

    // Build sidebar weapon context from WeaponAssignment state
    val (weaponCursor, assignedCurrent, assignedOthers) = when (attackPhase) {
        is PhaseState.Attack.WeaponAssignment -> {
            val currentTarget = attackPhase.targets.getOrNull(attackPhase.selectedTargetIndex)
            val currentWeaponIdx = currentTarget?.eligibleWeapons?.getOrNull(attackPhase.selectedWeaponIndex)?.weaponIndex
            val assignedToCurrentTarget = attackPhase.weaponAssignments[currentTarget?.unitId] ?: emptySet()
            val assignedToOthers = attackPhase.weaponAssignments.entries
                .filter { (k, _) -> k != currentTarget?.unitId }
                .flatMap { (_, v) -> v }
                .toSet()
            Triple(currentWeaponIdx, assignedToCurrentTarget, assignedToOthers)
        }
        else -> Triple(null, emptySet<Int>(), emptySet<Int>())
    }

    val sidebarView = SidebarView(
        unit = selectedUnit,
        weaponCursorIndex = weaponCursor,
        assignedToCurrentTarget = assignedCurrent,
        assignedToOtherTargets = assignedOthers,
    )
    sidebarView.render(buffer, boardWidth + targetsWidth, 0, sidebarWidth, boardHeight)

    val prompt = if (flash != null) flash.text else appState.phaseState.prompt
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
