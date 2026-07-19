package battletech.tactical.model

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitRoster
import kotlinx.serialization.Serializable

@Serializable
public data class GameState(
    public val units: UnitRoster<CombatUnit>,
    public val map: GameMap,
)
