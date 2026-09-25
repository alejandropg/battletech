package io.archinaut.battletech.tui.game.phase

import io.archinaut.battletech.tactical.attack.weapon.TargetInfo
import io.archinaut.battletech.tactical.unit.UnitId

internal data class AttackRender(
    val targets: List<TargetInfo>,
    val weaponAssignments: Map<UnitId, Set<Int>>,
    val primaryTargetId: UnitId?,
    val cursorTargetIndex: Int,
    val cursorWeaponIndex: Int,
)
