package battletech.tactical.session

import battletech.tactical.model.HexDirection
import battletech.tactical.unit.UnitId

public data class UnitDeclaration(
    val unitId: UnitId,
    val torsoFacing: HexDirection,
    val primaryTargetId: UnitId? = null,
    val weaponAssignments: Map<UnitId, Set<Int>> = emptyMap(),
)
