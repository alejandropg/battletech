package battletech.tactical.action

import battletech.tactical.model.ArmorLayout
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.InternalStructureLayout
import battletech.tactical.model.Weapon

public data class CombatUnit(
    public val id: UnitId,
    public val owner: PlayerId,
    public val name: String,
    public val gunnerySkill: Int,
    public val pilotingSkill: Int,
    public val weapons: List<Weapon>,
    public val position: HexCoordinates,
    public val facing: HexDirection = HexDirection.N,
    public val walkingMP: Int = 0,
    public val runningMP: Int = 0,
    public val jumpMP: Int = 0,
    public val currentHeat: Int = 0,
    public val heatSinkCapacity: Int,
    public val armor: ArmorLayout,
    public val internalStructure: InternalStructureLayout,
)
