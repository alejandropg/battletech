package battletech.tui.game.phase

import battletech.tactical.attack.AttackResult
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitRoster
import battletech.tactical.unit.VisibleUnit

internal data class AttackResultsRender(
    val results: List<AttackResult>,
    val units: UnitRoster<VisibleUnit>,
    /** Who this render is for — lets [battletech.tui.view.AttackResultsView] tell an own
     *  attacker from a foreign one (`units.byId(id).owner == viewer`) without re-deriving it. */
    val viewer: PlayerId,
)
