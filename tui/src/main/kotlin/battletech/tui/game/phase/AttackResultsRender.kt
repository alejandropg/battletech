package battletech.tui.game.phase

import battletech.tactical.attack.AttackResult
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitId

internal data class AttackResultsRender(
    val results: List<AttackResult>,
    val unitOwners: Map<UnitId, PlayerId>,
    /** Who this render is for — lets [battletech.tui.view.AttackResultsView] tell an own
     *  attacker from a foreign one (`unitOwners[id] == viewer`) without re-deriving it. */
    val viewer: PlayerId,
)
