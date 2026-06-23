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

    /**
     * Units a player can still activate this turn — excludes shutdown, destroyed,
     * and (Stage 7) unconscious-pilot 'Mechs. An unconscious pilot cannot act, but
     * the unit remains a valid target (see [battletech.tactical.query.WeaponTargeting.validTargets],
     * which only excludes [CombatUnit.isDestroyed]).
     */
    public fun activeUnitsOf(player: PlayerId): List<CombatUnit> =
        unitsOf(player).filter { !it.isShutdown && !it.isDestroyed && it.isPilotConscious }
}
