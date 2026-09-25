package io.archinaut.battletech.tactical.session

import io.archinaut.battletech.tactical.attack.AttackDeclaration
import io.archinaut.battletech.tactical.model.HexDirection
import io.archinaut.battletech.tactical.unit.UnitId

public data class UnitDeclaration(
    val unitId: UnitId,
    val torsoFacing: HexDirection,
    val primaryTargetId: UnitId? = null,
    val weaponAssignments: Map<UnitId, Set<Int>> = emptyMap(),
)

/** Flattens per-unit weapon drafts into the per-weapon declarations the engine consumes. */
public fun Collection<UnitDeclaration>.toAttackDeclarations(): List<AttackDeclaration> =
    flatMap { decl ->
        decl.weaponAssignments.flatMap { (targetId, weaponIndices) ->
            weaponIndices.map { weaponIndex ->
                AttackDeclaration(
                    attackerId = decl.unitId,
                    targetId = targetId,
                    weaponIndex = weaponIndex,
                    isPrimary = targetId == decl.primaryTargetId,
                )
            }
        }
    }
