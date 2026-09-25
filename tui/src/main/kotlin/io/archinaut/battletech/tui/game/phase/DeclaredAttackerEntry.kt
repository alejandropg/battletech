package io.archinaut.battletech.tui.game.phase

import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.unit.UnitId

internal data class DeclaredAttackerEntry(
    val attackerId: UnitId,
    val ownerPlayer: PlayerId,
    val isDraft: Boolean,
    val targets: List<DeclaredTargetEntry>,
)
