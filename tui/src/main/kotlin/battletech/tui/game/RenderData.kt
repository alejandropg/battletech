package battletech.tui.game

import battletech.tactical.attack.lineOfSight
import battletech.tactical.model.GameMap
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
    val selectedTargetPosition: HexCoordinates? = null,
) {
    public companion object {
        public val EMPTY: RenderData = RenderData()
    }
}

/**
 * Line-of-sight highlight hexes from [attackerPosition] to each of [targetPositions].
 * Positions only — [battletech.tactical.attack.lineOfSight] never needs anything else
 * about either unit, so this works identically whether a target is owned by the viewer
 * or not.
 */
internal fun losHighlights(
    attackerPosition: HexCoordinates,
    targetPositions: Set<HexCoordinates>,
    map: GameMap,
): Map<HexCoordinates, HexHighlight> =
    targetPositions.flatMap { losLine(attackerPosition, it, map) }.associateWith { HexHighlight.LINE_OF_SIGHT }

internal fun selectedLosHighlights(
    attackerPosition: HexCoordinates,
    targetPosition: HexCoordinates,
    map: GameMap,
): Map<HexCoordinates, HexHighlight> =
    losLine(attackerPosition, targetPosition, map).associateWith { HexHighlight.LINE_OF_SIGHT_SELECTED }

private fun losLine(attackerPosition: HexCoordinates, targetPosition: HexCoordinates, map: GameMap): List<HexCoordinates> {
    if (lineOfSight(attackerPosition, targetPosition, map).blocked) return emptyList()
    return attackerPosition.lineTo(targetPosition).drop(1).dropLast(1)
}

internal fun reachabilityHighlights(reachability: ReachabilityMap): Map<HexCoordinates, HexHighlight> {
    val highlight = when (reachability.mode) {
        MovementMode.WALK -> HexHighlight.REACHABLE_WALK
        MovementMode.RUN -> HexHighlight.REACHABLE_RUN
        MovementMode.JUMP -> HexHighlight.REACHABLE_JUMP
    }
    return reachability.destinations.associate { it.position to highlight }
}

internal fun pathHighlights(path: List<HexCoordinates>?): Map<HexCoordinates, HexHighlight> {
    if (path == null) return emptyMap()
    return path.dropLast(1).associateWith { HexHighlight.PATH }
}
