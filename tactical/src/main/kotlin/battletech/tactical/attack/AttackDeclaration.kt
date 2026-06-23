package battletech.tactical.attack

import battletech.tactical.unit.UnitId

public data class AttackDeclaration(
    val attackerId: UnitId,
    val targetId: UnitId,
    val weaponIndex: Int,
    val isPrimary: Boolean,
)
