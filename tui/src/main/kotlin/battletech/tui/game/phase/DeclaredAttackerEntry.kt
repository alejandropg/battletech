package battletech.tui.game.phase

import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitId

internal data class DeclaredAttackerEntry(
    val attackerId: UnitId,
    val ownerPlayer: PlayerId,
    val isDraft: Boolean,
    val targets: List<DeclaredTargetEntry>,
)
