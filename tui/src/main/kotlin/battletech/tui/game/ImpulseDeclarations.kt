package battletech.tui.game

import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.AttackDeclaration

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
