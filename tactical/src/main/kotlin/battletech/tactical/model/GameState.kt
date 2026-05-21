package battletech.tactical.model

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId

public data class GameState(
    public val units: List<CombatUnit>,
    public val map: GameMap,
) {
    public fun unitAt(position: HexCoordinates): CombatUnit? =
        units.find { it.position == position }

    public fun unitById(id: UnitId): CombatUnit? = units.find { it.id == id }
    public fun unitsOf(player: PlayerId): List<CombatUnit> = units.filter { it.owner == player }
}
