package battletech.tactical.attack

import battletech.tactical.model.UnitId

public data class TargetInfo(
    val unitId: UnitId,
    val unitName: String,
    val weapons: List<WeaponTargetInfo>,
)
