package battletech.tui.game

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.ReachabilityMap
import battletech.tui.hex.HexHighlight

public data class RenderData(
    val hexHighlights: Map<HexCoordinates, HexHighlight> = emptyMap(),
    val reachableFacings: Map<HexCoordinates, Set<HexDirection>> = emptyMap(),
    val facingSelection: FacingSelection? = null,
) {
    public companion object {
        public val EMPTY: RenderData = RenderData()
    }
}

public data class FacingSelection(
    val hex: HexCoordinates,
    val facings: Set<HexDirection>,
)

public fun extractRenderData(phaseState: PhaseState): RenderData {
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
        is PhaseState.Attack -> RenderData.EMPTY
    }
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
