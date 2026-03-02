package battletech.tui.game

import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.resolveAttacks
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex
import battletech.tui.input.AttackAction
import battletech.tui.input.BrowsingAction
import battletech.tui.input.IdleAction
import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

public sealed interface PhaseState {
    public val prompt: String

    public fun processEvent(
        event: InputEvent,
        appState: AppState,
        phaseManager: PhaseManager,
    ): HandleResult?

    public data class Idle(
        override val prompt: String = "Move cursor to select a unit",
    ) : PhaseState {

        override fun processEvent(
            event: InputEvent,
            appState: AppState,
            phaseManager: PhaseManager,
        ): HandleResult? {
            val action = when (event) {
                is KeyboardEvent -> InputMapper.mapIdleEvent(event)
                is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                    ?.let { IdleAction.ClickHex(it) }
            } ?: return null

            return when (action) {
                is IdleAction.MoveCursor -> {
                    val newCursor = moveCursor(appState.cursor, action.direction, appState.gameState.map)
                    HandleResult(appState.copy(cursor = newCursor))
                }

                is IdleAction.ClickHex -> {
                    val updated = appState.copy(cursor = action.coords)
                    trySelectUnit(updated, phaseManager)
                }

                is IdleAction.SelectUnit -> trySelectUnit(appState, phaseManager)
                is IdleAction.CycleUnit -> cycleUnit(appState, phaseManager)
                is IdleAction.CommitDeclarations -> commitDeclarations(appState, phaseManager)
            }
        }

        private fun trySelectUnit(appState: AppState, phaseManager: PhaseManager): HandleResult {
            val unit = appState.gameState.unitAt(appState.cursor)
                ?: return HandleResult(appState)
            val turnState = appState.turnState

            if (turnState != null && appState.currentPhase == TurnPhase.MOVEMENT) {
                when (validateUnitSelection(unit, turnState)) {
                    UnitSelectionResult.NOT_YOUR_UNIT ->
                        return HandleResult(appState, FlashMessage("Not your unit"))

                    UnitSelectionResult.ALREADY_MOVED, UnitSelectionResult.ALREADY_ACTED ->
                        return HandleResult(appState, FlashMessage("Already moved"))

                    UnitSelectionResult.VALID -> {}
                }
            }

            if (turnState != null && isAttackPhase(appState.currentPhase)) {
                when (validateAttackUnitSelection(unit, turnState)) {
                    UnitSelectionResult.NOT_YOUR_UNIT ->
                        return HandleResult(appState, FlashMessage("Not your unit"))

                    UnitSelectionResult.ALREADY_ACTED ->
                        return HandleResult(appState, FlashMessage("Already committed attacks"))

                    UnitSelectionResult.ALREADY_MOVED, UnitSelectionResult.VALID -> {}
                }
            }

            val newPhase = when (appState.currentPhase) {
                TurnPhase.MOVEMENT -> phaseManager.movementController.enter(unit, appState.gameState)
                TurnPhase.WEAPON_ATTACK -> phaseManager.attackController.enter(unit, TurnPhase.WEAPON_ATTACK, appState.gameState)
                TurnPhase.PHYSICAL_ATTACK -> phaseManager.attackController.enter(unit, TurnPhase.PHYSICAL_ATTACK, appState.gameState)
                else -> null
            }
            return if (newPhase != null) {
                HandleResult(appState.copy(phase = newPhase))
            } else {
                HandleResult(appState)
            }
        }

        private fun commitDeclarations(appState: AppState, phaseManager: PhaseManager): HandleResult {
            val turnState = appState.turnState
            if (turnState == null || !isAttackPhase(appState.currentPhase)) {
                return HandleResult(appState)
            }

            if (!phaseManager.attackController.canCommit()) {
                val declared = phaseManager.attackController.declaredCount()
                val total = phaseManager.attackController.currentImpulseUnitCount()
                return HandleResult(appState, FlashMessage("Declare all units first ($declared/$total declared)"))
            }

            val commitResult = phaseManager.attackController.commitImpulse()
            val gameStateWithTorso = applyTorsoFacings(appState.gameState, commitResult.torsoFacings)

            var newTurnState: TurnState = turnState
            for (unitId in commitResult.unitIds) {
                newTurnState = advanceAfterUnitAttacked(newTurnState, unitId)
            }

            return if (newTurnState.allAttackImpulsesComplete) {
                if (appState.currentPhase == TurnPhase.WEAPON_ATTACK) {
                    resolveWeaponAttacks(appState, gameStateWithTorso, newTurnState, phaseManager)
                } else {
                    HandleResult(
                        appState.copy(
                            gameState = gameStateWithTorso,
                            currentPhase = nextPhase(appState.currentPhase),
                            phase = Idle(),
                            turnState = newTurnState,
                        )
                    )
                }
            } else {
                phaseManager.attackController.initializeImpulse(
                    newTurnState.activeAttackPlayer,
                    newTurnState.currentAttackImpulse.unitCount,
                )
                HandleResult(
                    appState.copy(
                        gameState = gameStateWithTorso,
                        phase = Idle(buildAttackPrompt(newTurnState, phaseManager)),
                        turnState = newTurnState,
                    )
                )
            }
        }

        private fun resolveWeaponAttacks(
            appState: AppState,
            gameStateWithTorso: battletech.tactical.model.GameState,
            newTurnState: TurnState,
            phaseManager: PhaseManager,
        ): HandleResult {
            val declarations = phaseManager.attackController.collectDeclarations()
            val (resolvedGameState, flash) = if (declarations.isNotEmpty()) {
                val (resolved, results) = resolveAttacks(declarations, gameStateWithTorso, phaseManager.random)
                val hitCount = results.count { it.hit }
                val totalDamage = results.sumOf { it.damageApplied }
                resolved to FlashMessage("Attacks resolved: ${results.size} attacks, $hitCount hits, $totalDamage damage")
            } else {
                gameStateWithTorso to null
            }
            phaseManager.attackController.clearDeclarations()

            val physicalTurnState = newTurnState.copy(
                attackedUnitIds = emptySet(),
                currentAttackImpulseIndex = 0,
                unitsAttackedInCurrentImpulse = 0,
                attackOrder = emptyList(),
            )
            return HandleResult(
                appState.copy(
                    gameState = resolvedGameState,
                    currentPhase = nextPhase(TurnPhase.WEAPON_ATTACK),
                    phase = Idle(),
                    turnState = physicalTurnState,
                ),
                flash,
            )
        }

        private fun cycleUnit(appState: AppState, phaseManager: PhaseManager): HandleResult {
            val turnState = appState.turnState
            val units = when {
                turnState != null && appState.currentPhase == TurnPhase.MOVEMENT ->
                    selectableUnits(appState.gameState, turnState)

                turnState != null && isAttackPhase(appState.currentPhase) -> {
                    val all = selectableAttackUnits(appState.gameState, turnState)
                    val undeclared = all.filter { !phaseManager.attackController.isDeclared(it.id) }
                    undeclared.ifEmpty { all }
                }

                else -> appState.gameState.units
            }
            if (units.isEmpty()) return HandleResult(appState)

            val currentIdx = units.indexOfFirst { it.position == appState.cursor }
            val nextIdx = (currentIdx + 1) % units.size
            return HandleResult(appState.copy(cursor = units[nextIdx].position))
        }
    }

    public sealed interface Movement : PhaseState {
        public val unitId: UnitId
        public val modes: List<ReachabilityMap>
        public val currentModeIndex: Int
        public val reachability: ReachabilityMap get() = modes[currentModeIndex]

        public data class Browsing(
            override val unitId: UnitId,
            override val modes: List<ReachabilityMap>,
            override val currentModeIndex: Int,
            val hoveredPath: List<HexCoordinates>?,
            val hoveredDestination: ReachableHex?,
            override val prompt: String,
        ) : Movement {

            override fun processEvent(
                event: InputEvent,
                appState: AppState,
                phaseManager: PhaseManager,
            ): HandleResult? {
                val action = when (event) {
                    is KeyboardEvent -> InputMapper.mapBrowsingEvent(event)
                    is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                        ?.let { BrowsingAction.ClickHex(it) }
                } ?: return null

                val newCursor = when (action) {
                    is BrowsingAction.MoveCursor -> moveCursor(appState.cursor, action.direction, appState.gameState.map)
                    is BrowsingAction.ClickHex -> action.coords
                    else -> appState.cursor
                }
                val updated = appState.copy(cursor = newCursor)
                val outcome = phaseManager.movementController.handle(action, this, newCursor, updated.gameState)
                return phaseManager.fromOutcome(outcome, updated)
            }
        }

        public data class SelectingFacing(
            override val unitId: UnitId,
            override val modes: List<ReachabilityMap>,
            override val currentModeIndex: Int,
            val hex: HexCoordinates,
            val options: List<ReachableHex>,
            val path: List<HexCoordinates>,
            override val prompt: String,
        ) : Movement {

            override fun processEvent(
                event: InputEvent,
                appState: AppState,
                phaseManager: PhaseManager,
            ): HandleResult? {
                val action = when (event) {
                    is KeyboardEvent -> InputMapper.mapFacingEvent(event)
                    is MouseEvent -> return null
                } ?: return null

                val outcome = phaseManager.movementController.handle(action, this, appState.cursor, appState.gameState)
                return phaseManager.fromOutcome(outcome, appState)
            }
        }
    }

    /** Player twists torso and assigns weapons simultaneously. */
    public data class Attack(
        val unitId: UnitId,
        val attackPhase: TurnPhase,
        val torsoFacing: HexDirection,
        val arc: Set<HexCoordinates>,
        val validTargetIds: Set<UnitId>,
        val targets: List<TargetInfo>,
        val cursorTargetIndex: Int,
        val cursorWeaponIndex: Int,
        val weaponAssignments: Map<UnitId, Set<Int>>,
        val primaryTargetId: UnitId?,
        override val prompt: String,
    ) : PhaseState {

        override fun processEvent(
            event: InputEvent,
            appState: AppState,
            phaseManager: PhaseManager,
        ): HandleResult? {
            val action = when (event) {
                is KeyboardEvent -> InputMapper.mapAttackEvent(event)
                is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                    ?.let { AttackAction.ClickTarget(it) }
            } ?: return null

            val outcome = phaseManager.attackController.handle(action, this, appState.cursor, appState.gameState)
            val result = phaseManager.fromOutcome(outcome, appState)

            // After returning to Idle from attack, refresh prompt with declaration progress
            val newPhaseState = result.appState.phase
            if (newPhaseState is Idle && isAttackPhase(result.appState.currentPhase)) {
                val ts = result.appState.turnState
                if (ts != null && !ts.allAttackImpulsesComplete) {
                    return HandleResult(
                        result.appState.copy(phase = Idle(buildAttackPrompt(ts, phaseManager))),
                        result.flash,
                    )
                }
            }
            return result
        }
    }
}

public data class TargetInfo(
    val unitId: UnitId,
    val unitName: String,
    val weapons: List<WeaponTargetInfo>,
)

public data class WeaponTargetInfo(
    val weaponIndex: Int,
    val weaponName: String,
    val successChance: Int,
    val damage: Int,
    val modifiers: List<String>,
    val available: Boolean = true,
)

private fun isAttackPhase(phase: TurnPhase): Boolean =
    phase == TurnPhase.WEAPON_ATTACK || phase == TurnPhase.PHYSICAL_ATTACK

private fun buildAttackPrompt(turnState: TurnState, phaseManager: PhaseManager): String =
    attackPrompt(
        turnState,
        phaseManager.attackController.declaredCount(),
        phaseManager.attackController.currentImpulseUnitCount(),
    )
