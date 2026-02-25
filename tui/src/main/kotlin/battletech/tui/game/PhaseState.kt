package battletech.tui.game

import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.model.HexCoordinates
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.movement.ReachableHex

public sealed interface PhaseState {
    public val prompt: String

    public data class Idle(
        override val prompt: String = "Move cursor to select a unit",
    ) : PhaseState

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
        ) : Movement

        public data class SelectingFacing(
            override val unitId: UnitId,
            override val modes: List<ReachabilityMap>,
            override val currentModeIndex: Int,
            val hex: HexCoordinates,
            val options: List<ReachableHex>,
            val path: List<HexCoordinates>,
            override val prompt: String,
        ) : Movement
    }

    public data class Attack(
        val unitId: UnitId,
        val attackPhase: TurnPhase,
        override val prompt: String,
    ) : PhaseState
}
