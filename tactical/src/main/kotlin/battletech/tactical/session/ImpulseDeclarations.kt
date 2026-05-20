package battletech.tactical.session

import battletech.tactical.model.PlayerId
import battletech.tactical.model.UnitId
import battletech.tactical.attack.AttackDeclaration

public data class ImpulseDeclarations(
    val playerId: PlayerId,
    val declarations: Map<UnitId, UnitDeclaration> = emptyMap(),
) {
    public fun withDeclaration(decl: UnitDeclaration): ImpulseDeclarations =
        copy(declarations = declarations + (decl.unitId to decl))

    public fun toAttackDeclarations(): List<AttackDeclaration> =
        declarations.values.flatMap { decl ->
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
}
