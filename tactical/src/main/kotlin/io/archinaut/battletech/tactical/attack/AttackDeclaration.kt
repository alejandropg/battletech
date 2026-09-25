package io.archinaut.battletech.tactical.attack

import io.archinaut.battletech.tactical.unit.UnitId
import kotlinx.serialization.Serializable

@Serializable
public data class AttackDeclaration(
    val attackerId: UnitId,
    val targetId: UnitId,
    val weaponIndex: Int,
    val isPrimary: Boolean,
)
