package io.archinaut.battletech.tui.game.phase

import io.archinaut.battletech.tactical.unit.UnitId

internal data class DeclaredTargetEntry(
    val targetId: UnitId,
    val isPrimary: Boolean,
    val weapons: List<DeclaredWeaponEntry>,
)
