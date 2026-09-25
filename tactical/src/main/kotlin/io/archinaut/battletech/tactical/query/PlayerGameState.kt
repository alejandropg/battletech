package io.archinaut.battletech.tactical.query

import io.archinaut.battletech.tactical.model.GameMap
import io.archinaut.battletech.tactical.model.GameState
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.unit.CombatUnit
import io.archinaut.battletech.tactical.unit.ForeignUnit
import io.archinaut.battletech.tactical.unit.UnitId
import io.archinaut.battletech.tactical.unit.UnitRoster
import io.archinaut.battletech.tactical.unit.VisibleUnit
import kotlinx.serialization.Serializable

/**
 * Per-viewer projection of [GameState]: the same read surface, but [units] holds
 * [VisibleUnit]s instead of raw [CombatUnit]s — the [CombatUnit] itself for units the
 * viewer owns (or every unit, at the deliberate match-over reveal), [ForeignUnit]
 * otherwise. Built by [projectFor].
 */
@Serializable
public data class PlayerGameState(
    public val units: UnitRoster<VisibleUnit>,
    public val map: GameMap,
) {
    /**
     * The full [CombatUnit] for [id], for call sites that already know [id] names a unit the
     * viewer owns — the **actor** of a query (the mover, the attacker), which is by
     * definition always one of the viewer's own units, and whose record sheet the to-hit and
     * reachability math genuinely needs (see [io.archinaut.battletech.tactical.attack.AttackContext]).
     *
     * Throws if [id] projects as [ForeignUnit] instead: that means the call site's ownership
     * assumption was wrong, which must fail LOUDLY rather than silently degrade to a wrong
     * answer — and certainly rather than tempt a caller into fabricating the missing private
     * fields. The single source of this rule; [io.archinaut.battletech.tui.game.AppState]'s `ownUnit`
     * delegates here rather than restating it.
     */
    public fun ownUnitById(id: UnitId): CombatUnit =
        units.byId(id) as? CombatUnit
            ?: error("Expected $id to be the viewer's own unit, but it projected as foreign")
}

/**
 * Projects [GameState] for [viewer]: the [CombatUnit] itself for units [viewer] owns,
 * [ForeignUnit] otherwise.
 *
 * [revealAll] is the deliberate match-over reveal (every unit stays a [CombatUnit]),
 * supplied from `session.isMatchOver`.
 *
 * [viewer] `== null` means "I don't know who is looking": every unit becomes
 * [ForeignUnit], including what would otherwise be that unknown viewer's own units.
 * This fails CLOSED on purpose — the opposite (fail-open) was the live bug fixed in
 * `29c7576`; do not repeat it.
 */
public fun GameState.projectFor(viewer: PlayerId?, revealAll: Boolean = false): PlayerGameState =
    PlayerGameState(
        units = UnitRoster(
            units.all.map { unit ->
                if (revealAll || (viewer != null && unit.owner == viewer)) {
                    unit
                } else {
                    ForeignUnit.from(unit)
                }
            },
        ),
        map = map,
    )
