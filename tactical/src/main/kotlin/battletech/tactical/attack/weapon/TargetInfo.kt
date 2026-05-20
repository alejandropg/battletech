package battletech.tactical.attack.weapon

import battletech.tactical.model.UnitId

public data class TargetInfo(
    val unitId: UnitId,
    val unitName: String,
    val weapons: List<WeaponTargetInfo>,
)
