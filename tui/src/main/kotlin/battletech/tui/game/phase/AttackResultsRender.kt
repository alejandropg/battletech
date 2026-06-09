package battletech.tui.game.phase

import battletech.tactical.attack.AttackResult
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitId

internal data class AttackResultsRender(
    val results: List<AttackResult>,
    val unitNames: Map<UnitId, String>,
    val unitOwners: Map<UnitId, PlayerId>,
)
