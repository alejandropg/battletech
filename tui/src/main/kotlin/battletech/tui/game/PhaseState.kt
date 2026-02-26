package battletech.tui.game

import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
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

    public sealed interface Attack : PhaseState {
        public val unitId: UnitId
        public val attackPhase: TurnPhase
        public val torsoFacing: HexDirection
        public val arc: Set<HexCoordinates>
        public val validTargetIds: Set<UnitId>

        /** Initial state: player twists torso to aim, targets shown as read-only preview. */
        public data class TorsoFacing(
            override val unitId: UnitId,
            override val attackPhase: TurnPhase,
            override val torsoFacing: HexDirection,
            override val arc: Set<HexCoordinates>,
            override val validTargetIds: Set<UnitId>,
            val targets: List<TargetInfo>,
            override val prompt: String,
        ) : Attack

        /** Torso locked; player selects a target or "No Attack". */
        public data class TargetBrowsing(
            override val unitId: UnitId,
            override val attackPhase: TurnPhase,
            override val torsoFacing: HexDirection,
            override val arc: Set<HexCoordinates>,
            override val validTargetIds: Set<UnitId>,
            val targets: List<TargetInfo>,
            val selectedTargetIndex: Int,   // targets.size = "No Attack" entry
            val weaponAssignments: Map<UnitId, Set<Int>>,
            val primaryTargetId: UnitId?,
            override val prompt: String,
        ) : Attack

        /** Player assigns weapons to the selected target. */
        public data class WeaponAssignment(
            override val unitId: UnitId,
            override val attackPhase: TurnPhase,
            override val torsoFacing: HexDirection,
            override val arc: Set<HexCoordinates>,
            override val validTargetIds: Set<UnitId>,
            val targets: List<TargetInfo>,
            val selectedTargetIndex: Int,
            val selectedWeaponIndex: Int,
            val weaponAssignments: Map<UnitId, Set<Int>>,
            val primaryTargetId: UnitId,
            override val prompt: String,
        ) : Attack
    }
}

public data class TargetInfo(
    val unitId: UnitId,
    val unitName: String,
    val eligibleWeapons: List<WeaponTargetInfo>,
)

public data class WeaponTargetInfo(
    val weaponIndex: Int,
    val weaponName: String,
    val successChance: Int,
    val damage: Int,
    val modifiers: List<String>,
)
