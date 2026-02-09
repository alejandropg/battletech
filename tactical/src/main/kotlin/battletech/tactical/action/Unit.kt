package battletech.tactical.action

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Weapon

public data class Unit(
    val id: UnitId,
    val name: String,
    val gunnerySkill: Int,
    val pilotingSkill: Int = 5,
    val weapons: List<Weapon>,
    val position: HexCoordinates,
    val currentHeat: Int = 0,
    val heatSinkCapacity: Int = 10,
)
