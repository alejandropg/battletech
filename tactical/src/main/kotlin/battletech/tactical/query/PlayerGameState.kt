package battletech.tactical.query

import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.UnknownUnitException
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
     * Authoritative lookup by [id]. Throws [UnknownUnitException] if [id] does not name a
     * unit in this state. Mirrors [GameState.unitById] — see [UnknownUnitException] for why
     * an unknown id is a violated precondition rather than a handleable outcome.
     */
    public fun unitById(id: UnitId): VisibleUnit =
        units.find { it.id == id } ?: throw UnknownUnitException(id)

    /**
     * The full [CombatUnit] for [id], for call sites that already know [id] names a unit the
     * viewer owns — the **actor** of a query (the mover, the attacker), which is by
     * definition always one of the viewer's own units, and whose record sheet the to-hit and
     * reachability math genuinely needs (see [battletech.tactical.attack.AttackContext]).
     *
     * Throws if [id] projects as [ForeignUnit] instead: that means the call site's ownership
     * assumption was wrong, which must fail LOUDLY rather than silently degrade to a wrong
     * answer — and certainly rather than tempt a caller into fabricating the missing private
     * fields. The single source of this rule; [battletech.tui.game.AppState]'s `ownUnit`
     * delegates here rather than restating it.
     */
    public fun ownUnitById(id: UnitId): CombatUnit =
        (unitById(id) as? OwnUnit)?.unit
            ?: error("Expected $id to be the viewer's own unit, but it projected as foreign")

    public fun unitsOf(player: PlayerId): List<VisibleUnit> = units.filter { it.owner == player }

    /**
     * Units [player] can still activate this turn — excludes shutdown, destroyed, and
     * unconscious-pilot units. Mirrors [GameState.activeUnitsOf] exactly, for own and
     * foreign units alike: every field it tests ([VisibleUnit.isShutdown],
     * [VisibleUnit.isDestroyed], [VisibleUnit.isPilotConscious]) is observable and so is
     * present on both projections.
     */
    public fun activeUnitsOf(player: PlayerId): List<VisibleUnit> =
        unitsOf(player).filter { !it.isShutdown && !it.isDestroyed && it.isPilotConscious }
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
