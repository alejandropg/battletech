package battletech.tui.game

import battletech.tactical.action.UnitId

public data class TargetInfo(
    val unitId: UnitId,
    val unitName: String,
    val weapons: List<WeaponTargetInfo>,
)
