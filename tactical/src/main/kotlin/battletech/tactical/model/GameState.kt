package battletech.tactical.model

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
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
     * Authoritative lookup by [id]. Throws if [id] does not name a unit in this
     * state — callers must hold the invariant that [id] came from this same
     * [GameState] (e.g. a command field, an existing [CombatUnit.id]). Use
     * [findUnit] instead when [id] is unverified input that may legitimately not
     * resolve.
     */
    public fun unitById(id: UnitId): CombatUnit =
        units.find { it.id == id } ?: error("No unit with id $id")

    /**
     * Nullable probe by [id]: `null` when no unit with [id] exists in this state.
     * Use this (not [unitById]) for validation paths and other call sites where a
     * missing unit is an expected, handleable outcome rather than a programming error.
     */
    public fun findUnit(id: UnitId): CombatUnit? =
        units.find { it.id == id }

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
