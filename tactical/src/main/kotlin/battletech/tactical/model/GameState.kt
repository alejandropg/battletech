package battletech.tactical.model

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.UnknownUnitException
import kotlinx.serialization.Serializable

@Serializable
public data class GameState(
    public val units: List<CombatUnit>,
    public val map: GameMap,
) {
    /**
     * Spatial probe: the unit occupying [position], or `null` if the hex is empty.
     * Multiple units never share a position, so at most one match exists.
     */
    public fun unitAt(position: HexCoordinates): CombatUnit? =
        units.find { it.position == position }

    /**
     * Authoritative lookup by [id]. Throws [UnknownUnitException] if [id] does not name a
     * unit in this state. A [UnitId] never legitimately fails to resolve — units are never
     * removed from [units], destruction only flips [CombatUnit.isDestroyed] — so an unknown
     * id can only mean a bug or a tampered client, never a correctly-behaving one. See
     * [UnknownUnitException] for the full rationale.
     */
    public fun unitById(id: UnitId): CombatUnit =
        units.find { it.id == id } ?: throw UnknownUnitException(id)

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

public fun GameState.withUnit(unit: CombatUnit): GameState =
    copy(units = units.map { if (it.id == unit.id) unit else it })

public fun GameState.mapUnits(transform: (CombatUnit) -> CombatUnit): GameState =
    copy(units = units.map(transform))
