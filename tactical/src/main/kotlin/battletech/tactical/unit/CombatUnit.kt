package battletech.tactical.unit

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MechLocation
import battletech.tactical.model.PlayerId
import kotlinx.serialization.Serializable

@Serializable
public data class CombatUnit(
    override val id: UnitId,
    override val owner: PlayerId,
    override val name: String,
    override val tonnage: Int,
    public val gunnerySkill: Int,
    public val pilotingSkill: Int,
    override val weapons: List<Weapon>,
    override val position: HexCoordinates,
    override val facing: HexDirection = HexDirection.N,
    override val torsoFacing: HexDirection = facing,
    override val walkingMP: Int = 0,
    override val runningMP: Int = 0,
    override val jumpMP: Int = 0,
    public val currentHeat: Int = 0,
    public val heatSink: HeatSink,
    override val armor: ArmorLayout,
    public val internalStructure: InternalStructureLayout,
    public val criticalLayout: CriticalLayout = CriticalLayout.empty(),
    override val movementThisTurn: MovementThisTurn = MovementThisTurn.Stationary,
    public val heatGeneratedThisTurn: List<HeatSource> = emptyList(),
    override val isProne: Boolean = false,
    override val isShutdown: Boolean = false,
    override val isDestroyed: Boolean = false,
    public val criticalHits: Map<MechLocation, Set<Int>> = emptyMap(),
    public val pilotHits: Int = 0,
    override val isPilotConscious: Boolean = true,
) : VisibleUnit
