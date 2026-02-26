package battletech.tui.game

import battletech.tactical.action.UnitId
import battletech.tactical.model.HexDirection

public data class UnitDeclaration(
    val unitId: UnitId,
    val torsoFacing: HexDirection,
    val status: DeclarationStatus = DeclarationStatus.PENDING,
    val primaryTargetId: UnitId? = null,
    val weaponAssignments: Map<UnitId, Set<Int>> = emptyMap(),
)
