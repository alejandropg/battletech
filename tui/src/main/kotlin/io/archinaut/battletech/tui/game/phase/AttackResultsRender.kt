package io.archinaut.battletech.tui.game.phase

import io.archinaut.battletech.tactical.attack.AttackResult
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.unit.UnitRoster
import io.archinaut.battletech.tactical.unit.VisibleUnit

internal data class AttackResultsRender(
    val results: List<AttackResult>,
    val units: UnitRoster<VisibleUnit>,
    /** Who this render is for — lets [io.archinaut.battletech.tui.view.AttackResultsView] tell an own
     *  attacker from a foreign one (`units.byId(id).owner == viewer`) without re-deriving it. */
    val viewer: PlayerId,
)
