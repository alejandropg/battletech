package battletech.tui.game

import battletech.tactical.action.UnitId
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

public data class FacingSelection(
    val hex: HexCoordinates,
    val facings: Set<HexDirection>,
)

public fun extractRenderData(phaseState: PhaseState, gameState: GameState? = null): RenderData {
    return when (phaseState) {
        is PhaseState.Idle -> RenderData.EMPTY
        is PhaseState.Movement.Browsing -> RenderData(
            hexHighlights = reachabilityHighlights(phaseState.reachability)
                + pathHighlights(phaseState.hoveredPath),
            reachableFacings = phaseState.reachability.facingsByPosition(),
        )
        is PhaseState.Movement.SelectingFacing -> RenderData(
            hexHighlights = reachabilityHighlights(phaseState.modes[phaseState.currentModeIndex])
                + pathHighlights(phaseState.path),
            facingSelection = FacingSelection(
                phaseState.hex,
                phaseState.options.map { it.facing }.toSet(),
            ),
            reachableFacings = phaseState.modes[phaseState.currentModeIndex].facingsByPosition(),
        )
        is PhaseState.Attack -> {
            val arcHighlights = phaseState.arc.associateWith { HexHighlight.ATTACK_RANGE }
            val unitPos = gameState?.unitById(phaseState.unitId)?.position
            val torsoFacings = if (unitPos != null) mapOf(unitPos to phaseState.torsoFacing) else emptyMap()
            val targetPositions = resolveTargetPositions(phaseState.validTargetIds, gameState)
            RenderData(
                hexHighlights = arcHighlights,
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
