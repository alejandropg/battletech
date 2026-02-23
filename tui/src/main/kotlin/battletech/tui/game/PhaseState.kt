package battletech.tui.game

import battletech.tui.hex.HexHighlight
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.model.HexCoordinates
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex

public data class PhaseState(
    val phase: TurnPhase,
    val selectedUnitId: UnitId?,
    val reachability: ReachabilityMap? = null,
    val availableModes: List<ReachabilityMap> = emptyList(),
    val currentModeIndex: Int = 0,
    val highlightedPath: List<HexCoordinates>? = null,
    val selectedDestination: ReachableHex? = null,
    val prompt: String = "",
) {
    public fun hexHighlights(): Map<HexCoordinates, HexHighlight> {
        val highlights = mutableMapOf<HexCoordinates, HexHighlight>()

        reachability?.destinations?.forEach { dest ->
            highlights[dest.position] = HexHighlight.REACHABLE
        }

        highlightedPath?.forEach { pos ->
            highlights[pos] = HexHighlight.PATH
        }

        return highlights
    }
}
