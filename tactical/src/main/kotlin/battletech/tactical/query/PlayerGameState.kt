package battletech.tactical.query

import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitId
import kotlinx.serialization.Serializable

/**
 * Per-viewer projection of [GameState]: the same read surface, but [units] holds
 * [VisibleUnit]s instead of raw [battletech.tactical.unit.CombatUnit]s — [OwnUnit] for
 * units the viewer owns (or every unit, at the deliberate match-over reveal),
 * [ForeignUnit] otherwise. Built by [projectFor].
 */
@Serializable
public data class PlayerGameState(
    public val units: List<VisibleUnit>,
    public val map: GameMap,
) {
    /**
     * Spatial probe: the unit occupying [position], or `null` if the hex is empty.
     * Multiple units never share a position, so at most one match exists.
     */
    public fun unitAt(position: HexCoordinates): VisibleUnit? =
        units.find { it.position == position }

    /**
     * Authoritative lookup by [id]. Throws if [id] does not name a unit in this
     * state — callers must hold the invariant that [id] came from this same
     * [PlayerGameState] (e.g. a command field, an existing [VisibleUnit.id]). Use
     * [findUnit] instead when [id] is unverified input that may legitimately not
     * resolve.
     */
    public fun unitById(id: UnitId): VisibleUnit =
        units.find { it.id == id } ?: error("No unit with id $id")

    /**
     * Nullable probe by [id]: `null` when no unit with [id] exists in this state.
     * Use this (not [unitById]) for validation paths and other call sites where a
     * missing unit is an expected, handleable outcome rather than a programming error.
     */
    public fun findUnit(id: UnitId): VisibleUnit? =
        units.find { it.id == id }

    public fun unitsOf(player: PlayerId): List<VisibleUnit> = units.filter { it.owner == player }

    /**
     * Units [player] can still activate this turn — excludes shutdown and destroyed
     * units, mirroring [GameState.activeUnitsOf]. Unlike that method, this canNOT also
     * exclude an unconscious pilot for a [ForeignUnit]: pilot consciousness is private
     * and simply absent from the projection for units the viewer doesn't own, so a
     * [ForeignUnit] is never excluded on that basis here. This method is only
     * meaningful for `player == ` the viewer that produced this [PlayerGameState] (own
     * units project as [OwnUnit], where the full exclusion applies); calling it for the
     * opponent silently loses the pilot-consciousness filter rather than guessing.
     */
    public fun activeUnitsOf(player: PlayerId): List<VisibleUnit> =
        unitsOf(player).filter { unit ->
            !unit.isShutdown && !unit.isDestroyed &&
                when (unit) {
                    is OwnUnit -> unit.unit.isPilotConscious
                    is ForeignUnit -> true
                }
        }
}

/**
 * Projects [GameState] for [viewer]: [OwnUnit] for units [viewer] owns, [ForeignUnit]
 * otherwise.
 *
 * [revealAll] is the deliberate match-over reveal (every unit becomes [OwnUnit]) — a
 * later stage supplies it from `session.isMatchOver`.
 *
 * [viewer] `== null` means "I don't know who is looking": every unit becomes
 * [ForeignUnit], including what would otherwise be that unknown viewer's own units.
 * This fails CLOSED on purpose — the opposite (fail-open) was the live bug fixed in
 * `29c7576`; do not repeat it.
 */
public fun GameState.projectFor(viewer: PlayerId?, revealAll: Boolean = false): PlayerGameState =
    PlayerGameState(
        units = units.map { unit ->
            if (revealAll || (viewer != null && unit.owner == viewer)) {
                OwnUnit(unit)
            } else {
                ForeignUnit.from(unit)
            }
        },
        map = map,
    )
