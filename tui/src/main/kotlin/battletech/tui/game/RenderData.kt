package battletech.tui.game

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.RuleResult
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.PhysicalAttackContext
import battletech.tactical.action.attack.rule.LineOfSightRule
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.ReachabilityMap
import battletech.tui.hex.HexHighlight

public data class RenderData(
    val hexHighlights: Map<HexCoordinates, HexHighlight> = emptyMap(),
    val reachableFacings: Map<HexCoordinates, Set<HexDirection>> = emptyMap(),
    val facingSelection: FacingSelection? = null,
    val torsoFacings: Map<HexCoordinates, HexDirection> = emptyMap(),
    val validTargetPositions: Set<HexCoordinates> = emptySet(),
) {
    public companion object {
        public val EMPTY: RenderData = RenderData()
    }
}

public fun extractRenderData(phaseState: PhaseState, gameState: GameState? = null): RenderData {
    return when (phaseState) {
        is IdlePhaseState -> RenderData.EMPTY

        is MovementPhaseState.Browsing -> RenderData(
            hexHighlights = reachabilityHighlights(phaseState.reachability)
                    + pathHighlights(phaseState.hoveredPath),
            reachableFacings = phaseState.reachability.facingsByPosition(),
        )

        is MovementPhaseState.SelectingFacing -> RenderData(
            hexHighlights = reachabilityHighlights(phaseState.modes[phaseState.currentModeIndex])
                    + pathHighlights(phaseState.path),
            facingSelection = FacingSelection(
                phaseState.hex,
                phaseState.options.map { it.facing }.toSet(),
            ),
            reachableFacings = phaseState.modes[phaseState.currentModeIndex].facingsByPosition(),
        )

        is AttackPhaseState -> {
            val arcHighlights = phaseState.arc.associateWith { HexHighlight.ATTACK_RANGE }
            val unitPos = gameState?.unitById(phaseState.unitId)?.position
            val torsoFacings = if (unitPos != null) mapOf(unitPos to phaseState.torsoFacing) else emptyMap()
            val targetPositions = resolveTargetPositions(phaseState.validTargetIds, gameState)
            val los = if (gameState != null) losHighlights(phaseState, gameState) else emptyMap()
            val selectedLos = if (gameState != null) selectedLosHighlights(phaseState, gameState) else emptyMap()
            RenderData(
                hexHighlights = arcHighlights + los + selectedLos,
                torsoFacings = torsoFacings,
                validTargetPositions = targetPositions,
            )
        }
    }
}

private fun resolveTargetPositions(targetIds: Set<UnitId>, gameState: GameState?): Set<HexCoordinates> {
    if (gameState == null) return emptySet()
    return targetIds.mapNotNull { gameState.unitById(it)?.position }.toSet()
}

private fun losHighlights(phaseState: AttackPhaseState, gameState: GameState): Map<HexCoordinates, HexHighlight> {
    val attacker = gameState.unitById(phaseState.unitId) ?: return emptyMap()
    return phaseState.validTargetIds.flatMap { targetId ->
        val target = gameState.unitById(targetId) ?: return@flatMap emptyList()
        losLine(attacker, target, gameState)
    }.associateWith { HexHighlight.LINE_OF_SIGHT }
}

private fun selectedLosHighlights(phaseState: AttackPhaseState, gameState: GameState): Map<HexCoordinates, HexHighlight> {
    val targets = phaseState.targets
    val idx = phaseState.cursorTargetIndex
    if (idx !in targets.indices) return emptyMap()
    val attacker = gameState.unitById(phaseState.unitId) ?: return emptyMap()
    val target = gameState.unitById(targets[idx].unitId) ?: return emptyMap()
    return losLine(attacker, target, gameState).associateWith { HexHighlight.LINE_OF_SIGHT_SELECTED }
}

private fun losLine(attacker: CombatUnit, target: CombatUnit, gameState: GameState): List<HexCoordinates> {
    val context = PhysicalAttackContext(actor = attacker, gameState = gameState, target = target)
    if (LineOfSightRule().evaluate(context) != RuleResult.Satisfied) return emptyList()
    return attacker.position.lineTo(target.position).drop(1).dropLast(1)
}

private fun reachabilityHighlights(reachability: ReachabilityMap): Map<HexCoordinates, HexHighlight> {
    val highlight = when (reachability.mode) {
        MovementMode.WALK -> HexHighlight.REACHABLE_WALK
        MovementMode.RUN -> HexHighlight.REACHABLE_RUN
        MovementMode.JUMP -> HexHighlight.REACHABLE_JUMP
    }
    return reachability.destinations.associate { it.position to highlight }
}

private fun pathHighlights(path: List<HexCoordinates>?): Map<HexCoordinates, HexHighlight> {
    if (path == null) return emptyMap()
    return path.dropLast(1).associateWith { HexHighlight.PATH }
}
