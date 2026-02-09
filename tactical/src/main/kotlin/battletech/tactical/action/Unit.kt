package battletech.tactical.action

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Weapon

public data class Unit(
    public val id: UnitId,
    public val name: String,
    public val gunnerySkill: Int,
    public val pilotingSkill: Int = 5,
    public val weapons: List<Weapon>,
    public val position: HexCoordinates,
    public val currentHeat: Int = 0,
    public val heatSinkCapacity: Int = 10,
)
