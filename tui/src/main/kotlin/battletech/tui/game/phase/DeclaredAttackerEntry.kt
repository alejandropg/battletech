package battletech.tui.game.phase

import battletech.tactical.model.PlayerId

internal data class DeclaredAttackerEntry(
    val attackerName: String,
    val ownerPlayer: PlayerId,
    val isDraft: Boolean,
    val targets: List<DeclaredTargetEntry>,
)
