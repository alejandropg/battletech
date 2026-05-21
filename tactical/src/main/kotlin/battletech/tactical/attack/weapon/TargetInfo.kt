package battletech.tactical.attack.weapon

import battletech.tactical.unit.UnitId

public data class TargetInfo(
    val unitId: UnitId,
    val unitName: String,
    val weapons: List<WeaponTargetInfo>,
)
