package battletech.tui.game.phase

import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.unit.UnitId

internal data class AttackRender(
    val targets: List<TargetInfo>,
    val weaponAssignments: Map<UnitId, Set<Int>>,
    val primaryTargetId: UnitId?,
    val cursorTargetIndex: Int,
    val cursorWeaponIndex: Int,
)
